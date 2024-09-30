/*
 * Copyright (C) 2024 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "Fingerprint.h"

#include <fingerprint.sysprop.h>

#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <cutils/properties.h>

namespace {

typedef struct fingerprint_hal {
    const char* class_name;
} fingerprint_hal_t;

static const fingerprint_hal_t kModules[] = {
        {"fpc"},
        {"fpc_fod"},
        {"goodix"},
        {"goodix_fod"},
        {"goodix_fod6"},
        {"silead"},
        {"syna"},
};

}  // anonymous namespace

using namespace ::android::fingerprint::xiaomi;

using ::android::base::ParseInt;
using ::android::base::Split;

namespace aidl {
namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {

namespace {
constexpr int SENSOR_ID = 0;
constexpr common::SensorStrength SENSOR_STRENGTH = common::SensorStrength::STRONG;
constexpr int MAX_ENROLLMENTS_PER_USER = 7;
constexpr bool SUPPORTS_NAVIGATION_GESTURES = false;
constexpr char HW_COMPONENT_ID[] = "fingerprintSensor";
constexpr char HW_VERSION[] = "vendor/model/revision";
constexpr char FW_VERSION[] = "1.01";
constexpr char SERIAL_NUMBER[] = "00000001";
constexpr char SW_COMPONENT_ID[] = "matchingAlgorithm";
constexpr char SW_VERSION[] = "vendor/version/revision";

}  // namespace

static const uint16_t kVersion = HARDWARE_MODULE_API_VERSION(2, 1);
static Fingerprint* sInstance;

Fingerprint::Fingerprint()
    : mMaxEnrollmentsPerUser(MAX_ENROLLMENTS_PER_USER),
      mSupportsGestures(false),
      mDevice(nullptr),
      mUdfpsHandlerFactory(nullptr),
      mUdfpsHandler(nullptr) {
    sInstance = this;  // keep track of the most recent instance

    std::string sensorTypeProp = FingerprintHalProperties::type().value_or("");
    if (sensorTypeProp == "" || sensorTypeProp == "default" || sensorTypeProp == "rear")
        mSensorType = FingerprintSensorType::REAR;
    else if (sensorTypeProp == "udfps")
        mSensorType = FingerprintSensorType::UNDER_DISPLAY_ULTRASONIC;
    else if (sensorTypeProp == "udfps_optical")
        mSensorType = FingerprintSensorType::UNDER_DISPLAY_OPTICAL;
    else if (sensorTypeProp == "side")
        mSensorType = FingerprintSensorType::POWER_BUTTON;
    else if (sensorTypeProp == "home")
        mSensorType = FingerprintSensorType::HOME_BUTTON;
    else
        mSensorType = FingerprintSensorType::UNKNOWN;

    for (auto& [class_name] : kModules) {
        mDevice = openHal(class_name);
        if (!mDevice) {
            ALOGE("Can't open HAL module, class %s", class_name);
            continue;
        }

        ALOGI("Opened fingerprint HAL, class %s", class_name);
        break;
    }

    if (!mDevice) {
        ALOGE("Can't open any HAL module");
    }

    if (sensorTypeProp == "udfps" || sensorTypeProp == "udfps_optical"){
        ALOGI("UNDER_DISPLAY_OPTICAL selected");
        mUdfpsHandlerFactory = getUdfpsHandlerFactory();

        if (!mUdfpsHandlerFactory) {
            ALOGE("Can't get UdfpsHandlerFactory");
        } else {
            mUdfpsHandler = mUdfpsHandlerFactory->create();

            if (!mUdfpsHandler) {
                ALOGE("Can't create UdfpsHandler");
            } else {
                mUdfpsHandler->init(mDevice);
            }
        }
    }
}

fingerprint_device_t* Fingerprint::openHal(const char* class_name) {
    const hw_module_t* hw_mdl = nullptr;

    ALOGD("Opening fingerprint hal library...");
    if (hw_get_module_by_class(FINGERPRINT_HARDWARE_MODULE_ID, class_name, &hw_mdl) != 0) {
        ALOGE("Can't open fingerprint HW Module");
        return nullptr;
    }

    if (!hw_mdl) {
        ALOGE("No valid fingerprint module");
        return nullptr;
    }

    auto module = reinterpret_cast<const fingerprint_module_t*>(hw_mdl);
    if (!module->common.methods->open) {
        ALOGE("No valid open method");
        return nullptr;
    }

    hw_device_t* device = nullptr;
    if (module->common.methods->open(hw_mdl, nullptr, &device) != 0) {
        ALOGE("Can't open fingerprint methods");
        return nullptr;
    }

    auto fp_device = reinterpret_cast<fingerprint_device_t*>(device);
    if (fp_device->set_notify(fp_device, Fingerprint::notify) != 0) {
        ALOGE("Can't register fingerprint module callback");
        return nullptr;
    }

    return fp_device;
}

Fingerprint::~Fingerprint() {
    ALOGV("~Fingerprint()");
    if (mUdfpsHandler) {
        mUdfpsHandlerFactory->destroy(mUdfpsHandler);
    }
    if (mDevice == nullptr) {
        ALOGE("No valid device");
        return;
    }
    int err;
    if (0 != (err = mDevice->common.close(reinterpret_cast<hw_device_t*>(mDevice)))) {
        ALOGE("Can't close fingerprint module, error: %d", err);
        return;
    }
    mDevice = nullptr;
}

void Fingerprint::notify(const fingerprint_msg_t* msg) {
    Fingerprint* thisPtr = sInstance;
    if (thisPtr == nullptr || thisPtr->mSession == nullptr || thisPtr->mSession->isClosed()) {
        ALOGE("Receiving callbacks before a session is opened.");
        return;
    }
    thisPtr->mSession->notify(msg);
}

ndk::ScopedAStatus Fingerprint::getSensorProps(std::vector<SensorProps>* out) {
    std::vector<common::ComponentInfo> componentInfo = {
        {HW_COMPONENT_ID, HW_VERSION, FW_VERSION, SERIAL_NUMBER, "" /* softwareVersion */},
        {SW_COMPONENT_ID, "" /* hardwareVersion */, "" /* firmwareVersion */,
         "" /* serialNumber */, SW_VERSION}
    };

    common::CommonProps commonProps = {
        SENSOR_ID, SENSOR_STRENGTH, mMaxEnrollmentsPerUser, componentInfo
    };

    SensorLocation sensorLocation;
    std::string loc = FingerprintHalProperties::sensor_location().value_or("");
    std::vector<std::string> dim = Split(loc, "|");
    if (dim.size() >= 3 && dim.size() <= 4) {
        ParseInt(dim[0], &sensorLocation.sensorLocationX);
        ParseInt(dim[1], &sensorLocation.sensorLocationY);
        ParseInt(dim[2], &sensorLocation.sensorRadius);

        if (dim.size() >= 4)
            sensorLocation.display = dim[3];
    } else if(loc.length() > 0) {
        LOG(WARNING) << "Invalid sensor location input (x|y|radius|display): " << loc;
    }

    LOG(INFO) << "Sensor type: " << ::android::internal::ToString(mSensorType)
              << " location: " << sensorLocation.toString();

    *out = {{
        commonProps,
        mSensorType,
        {sensorLocation},
        mSupportsGestures,
        false,
        false,
        false,
        std::nullopt
    }};

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Fingerprint::createSession(int32_t /*sensorId*/, int32_t userId,
                                              const std::shared_ptr<ISessionCallback>& cb,
                                              std::shared_ptr<ISession>* out) {
    CHECK(mSession == nullptr || mSession->isClosed()) << "Open session already exists!";

    mSession = SharedRefBase::make<Session>(mDevice, mUdfpsHandler, userId, cb, mLockoutTracker);
    *out = mSession;

    mSession->linkToDeath(cb->asBinder().get());

    return ndk::ScopedAStatus::ok();
}

} // namespace fingerprint
} // namespace biometrics
} // namespace hardware
} // namespace android
} // namespace aidl
