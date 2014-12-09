all: SXRS.jar SXRS_winexe.jar

SXRS.jar: SXRS.class SXRS_Manifest.MF  images/SXRS-icon.gif html/helpJavaSXRS.html bash/sxrs_ssh.bash
	jar cfmv SXRS.jar SXRS_Manifest.MF SXRS.class SXRS\$$*.class images/SXRS-icon.gif html/helpJavaSXRS.html  bash/sxrs_ssh.bash
#	jarsigner -verbose -keystore ~/java/sxrsKeys SXRS.jar sxrs

SXRS_winexe.jar: win_exe/*
	jar cfv SXRS_winexe.jar win_exe/*

VncViewer.jar: tightvnc_javasrc/VncViewer.jar
	cp tightvnc_javasrc/VncViewer.jar VncViewer.jar
	jar ufMm  VncViewer.jar VncViewer_MANIFEST.MF
	jar ufm  VncViewer.jar VncViewer_MANIFEST.MF
#	jarsigner -verbose -keystore ~/java/sxrsKeys VncViewer.jar sxrs

# set BOOTCLASSPATH_1_6 to a default if not prefined
BOOTCLASSPATH_1_6 ?= /usr/lib/jvm/jre-1.6.0-openjdk/lib/rt.jar

SXRS.class: SXRS.java
	javac -source 1.6 -target 1.6 -bootclasspath $(BOOTCLASSPATH_1_6) -classpath VncViewer.jar SXRS.java

clean:
	rm -f SXRS.class SXRS.jar SXRS_winexe.jar SXRS\$$*.class sxrs_vncviewer.log sxrs_putty.exe sxrs_vncviewer.exe ssh_rosita.sh sxrs_ssh.bash
