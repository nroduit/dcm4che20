package org.dcm4che6.img.util;

public class NativeLibrary {

    public static String getNativeLibSpecification() {
        // http://www.osgi.org/Specifications/Reference
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        if (osName.toLowerCase().startsWith("win")) {
            // All Windows versions with a specific processor architecture (x86 or x86-64) are grouped under
            // windows. If you need to make different native libraries for the Windows versions, define it in the
            // Bundle-NativeCode tag of the bundle fragment.
            osName = "windows";
        } else if (osName.equals("Mac OS X")) {
            osName = "macosx";
        } else if (osName.equals("SymbianOS")) {
            osName = "epoc32";
        } else if (osName.equals("hp-ux")) {
            osName = "hpux";
        } else if (osName.equals("Mac OS")) {
            osName = "macos";
        } else if (osName.equals("OS/2")) {
            osName = "os2";
        } else if (osName.equals("procnto")) {
            osName = "qnx";
        } else {
            osName = osName.toLowerCase();
        }

        if (osArch.equals("pentium") || osArch.equals("i386") || osArch.equals("i486") || osArch.equals("i586")
                || osArch.equals("i686")) {
            osArch = "x86";
        } else if (osArch.equals("amd64") || osArch.equals("em64t") || osArch.equals("x86_64")) {
            osArch = "x86-64";
        } else if (osArch.equals("power ppc")) {
            osArch = "powerpc";
        } else if (osArch.equals("psc1k")) {
            osArch = "ignite";
        } else {
            osArch = osArch.toLowerCase();
        }
        return osName + "-" + osArch;
    }

    public static void main(String[] args) {
        System.out.println(getNativeLibSpecification());
    }
}
