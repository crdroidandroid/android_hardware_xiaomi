package vendor.xiaomi.hardware.displayfeature_aidl;

@VintfStability
interface IDisplayFeatureCallback {
  float displayfeatureInfoChanged(int displayId, int caseId, float modeId, float cookie);
}
