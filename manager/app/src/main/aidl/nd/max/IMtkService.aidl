// File: src/main/aidl/nd/max/IMtkService.aidl
//
// Fast root-IPC channel used by MtkUtils/MtkViewModel to read/write MTK sysfs
// nodes directly (uid 0), without spawning a new `su -c` shell for every call.
//
// Ported from ZKM (com.zuan.kernelmanager.IMtkService) to close the gap where
// this project shipped the interface/manifest entry for a bound root service
// but never implemented or bound one (see MtkRootService.kt / RootIpcManager.kt).
package nd.max;

interface IMtkService {
    String readNode(String path);
    boolean writeNode(String path, String value);
    boolean nodeExists(String path);
    List<String> listDirectories(String path);

    // قراءة عدة عقد في **معاملة واحدة**: القيمة i تقابل المسار i، وفارغ = لم تُقرأ.
    // السبب مقيس: كل `readNode` معاملة binder كاملة، ومسح الحرارة يقرأ عقدتين لكل منطقة
    // (وعشرات المناطق) ⇒ مئات المعاملات كل دورتين. الدفعة تنزل بها إلى واحدة، والقراءات
    // تبقى قراءات ملفات محلية داخل عملية الجذر (uid 0).
    List<String> readNodes(in List<String> paths);
}
