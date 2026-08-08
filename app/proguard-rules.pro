# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Apache POI missing class warnings
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn javax.xml.**
-dontwarn org.apache.poi.**

# Keep Apache POI and XMLBeans
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class org.apache.commons.compress.** { *; }

# Keep Aalto XML for POI
-keep class com.fasterxml.aalto.** { *; }
-keep class javax.xml.stream.** { *; }

# Fix for missing classes inside org.openxmlformats and org.tukaani.xz
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.tukaani.xz.**
-keep class org.tukaani.xz.** { *; }

# Fix for missing Commons Compress optional dependencies
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**

# Additional Apache POI rules for Android to prevent runtime crashes
-dontwarn org.apache.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn com.microsoft.schemas.**
-dontwarn com.graphbuilder.**

-dontnote org.apache.**
-dontnote org.openxmlformats.schemas.**
-dontnote org.etsi.**
-dontnote org.w3.**
-dontnote com.microsoft.schemas.**
-dontnote com.graphbuilder.**

-keeppackagenames org.apache.poi.ss.formula.function
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class org.apache.xmlbeans.impl.schema.BuiltinSchemaTypeSystem { public static *** get*(); }

