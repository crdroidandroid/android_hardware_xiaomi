#
# Copyright (C) 2025 AOSPA Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Euicc path
XIAOMI_EUICC := hardware/xiaomi/Euicc

# Soong Namespace
PRODUCT_SOONG_NAMESPACES += \
   $(XIAOMI_EUICC)

# Sepolicy
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(XIAOMI_EUICC)/sepolicy/private
    
# XiaomiEuicc
PRODUCT_PACKAGES += \
    XiaomiEuiccGoogle \
    XiaomiFrameworksEuicc

PRODUCT_PACKAGES += \
    XiaomiEuicc

# Blobs
PRODUCT_PACKAGES += \
    mirilhook \
    qcrilhook

# Permissions
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.telephony.euicc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.euicc.xml

