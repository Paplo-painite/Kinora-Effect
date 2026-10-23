# Kinora Effect for Android

هذه النسخة تبني تطبيق **EffectCraft** مفتوح المصدر على Android مع هوية Kinora: اسم العرض **Kinora Effect**، ومعرّف الحزمة `com.kinora.effect`، وأيقونة خاصة. لا يعيد هذا المستودع كتابة التطبيق المكتبي؛ بل يجلب مصدر EffectCraft الرسمي أثناء البناء ويستخدم مشغّل Android مخصصًا له.

## البناء

```bash
./build.sh       # APK إصدار Release موقّع محليًا
./build-debug.sh # APK للتصحيح
```

ستظهر الملفات في `android-builder/output/` بأسماء مثل `kinora-effect-<version>.apk`. يتطلب البناء Linux x86_64 وRust 1.95 أو أحدث وJava 17–23 واتصال إنترنت. يحاول المشغّل تنزيل Android SDK وJDK المطلوبين عند غيابهما. خيار `-appicon=/path/to/icon.png` يتيح استخدام أيقونة بديلة.

يحفظ المشغّل مفتاح التوقيع محليًا في `android-builder/keys/kinora-effect.jks`. احتفظ بهذا المفتاح لتمكين تثبيت تحديثات APK الموقعة بالمفتاح نفسه، ولا ترفعه إلى GitHub. هذا المفتاح المحلي لا يُعد مفتاح نشر رسميًا لمتجر Google Play.

## حدود النسخة

مصدر التطبيق الذي يُبنى هو [EffectCraft الرسمي](https://github.com/storytold/effectcraft)، وهو مشروع مفتوح المصدر ما يزال في مرحلة ألفا. التغييرات هنا تخص حزمة Android والعلامة التجارية والبناء وفحص الإصدارات؛ ولا تدّعي إصلاح كل عيوب التطبيق أو إعادة كتابة وظائفه. نجاح البناء لا يثبت عمل كل ميزة على كل جهاز، لذا يلزم اختبار APK على جهاز Android فعلي.

يتحقق التطبيق من الإصدارات الجديدة من صفحة [إصدارات Kinora Effect](https://github.com/Paplo-painite/Kinora-Effect/releases). لن يظهر تحديث حتى تُنشر إصدارات هناك بعلامات `vX.Y.Z` وتُرفق ملفات APK المناسبة.

## الترخيص والمصادر

ملفات Kinora الخاصة مرخصة وفق MIT. يتضمن `android-builder/` كودًا من [artcraft-android](https://github.com/MostafaXS1/artcraft-android) مع الاحتفاظ بإشعار MIT الأصلي. يُجلب EffectCraft من مستودعه أثناء البناء، وهو مرخص وفق MIT أو Apache-2.0؛ أما التبعيات فتحتفظ بتراخيصها الأصلية. راجع [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) وملفات الترخيص المضمنة.
