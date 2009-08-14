all: SXRS.jar SXRS_winexe.jar

SXRS.jar: SXRS.class SXRS_Manifest.MF  images/SXRS-icon.gif html/helpJavaSXRS.html bash/sxrs_ssh.bash
	jar cfmv SXRS.jar SXRS_Manifest.MF SXRS.class SXRS\$$TerminalRunner.class images/SXRS-icon.gif html/helpJavaSXRS.html  bash/sxrs_ssh.bash
#	jarsigner -verbose -keystore ~/java/sxrsKeys SXRS.jar sxrs

SXRS_winexe.jar: win_exe/*
	jar cfv SXRS_winexe.jar win_exe/*

SXRS.class: SXRS.java
	javac -source 1.4 -target 1.4 -classpath VncViewer.jar SXRS.java

clean:
	rm -f SXRS.class SXRS.jar SXRS_winexe.jar SXRS\$$*.class sxrs_vncviewer.log sxrs_putty.exe sxrs_vncviewer.exe ssh_rosita.sh sxrs_ssh.bash
