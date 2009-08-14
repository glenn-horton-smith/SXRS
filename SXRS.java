/** Java application for making pretty GUI interface to
    Secure eXperiment remote shift text terminal and VNC viewer.

    Notes to developers:
    
     * Code in SXRS.java is for GUI up to separator lines "// *+*+*+*+*+...".
       Code after the separator line is for actually launching the
       SXRS terminal and vnc viewers.
       
    @author: Glenn Horton-Smith
    @date: 2006/05/20
    @date: 2009/08/13
*/

import java.io.*;
import java.net.*;
import java.awt.*;
import java.applet.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.util.prefs.*;
import java.util.*;

public class SXRS extends Applet
    implements WindowListener, ActionListener {

    public static final String version="3.000";

    TextField tf_userId;                 // default user ID to use
    JCheckBox ckb_viewonly;              // view only flag for vncviewer
    TextField tf_loginChain;		 // user@host opts ! user@host opts...
    JComboBox cb_vncserverHostDisplays;	 // e.g. vncsrv01:1 vncsrv02:1
    TextField tf_localVncPortStart;	 // port on localhost for VNC tunnel
    TextField tf_localLoginPortStart;	 // port on localhost for ssh tunnel
    JComboBox cb_xtermCommandName;	 // terminal command name
    JComboBox cb_vncviewerCommandName;	 // vncviewer command name
    Button b_advancedSettingsToggle;	 // show/hide advanced settings
    Box    pnl_advancedSettings;	 // the advanced settings panel
    Box    pnl_startTerminalButtons;	 // the panel of "start ssh #n" buttons
    Box    pnl_startViewerButtons;	 // the panel of "start vnc #n" buttons
    Button[] ba_startTerminal;		 // the "start ssh #n" buttons
    Button[] ba_startViewer;		 // the "start vnc #n" buttons
    Button b_help;			 // the help button
    JFrame helpFrame;                    // the help window

    String userId;            // validated default user ID from tf_userId
    int n_loginHosts=0;       // number of hosts parsed from loginChain
    String[] loginHosts;      // hosts parsed from loginChain
    String[] loginUsers;      // users parsed from loginChain
    String[] loginOptions;    // options parsed from loginChain
    int [] loginPorts;        // local port set up by session i for next login
    // e.g., loginPorts[0] is the port to use to log in to the 2nd session.
    // Note for now loginPorts[i]= i+localLoginPortStart.
    // Someday if automatic port selection were implemented, then
    // the tf_localLoginPortStart text field will go away.
    
    Preferences myprefs;              // preferences database
    String os;		              // operating system
    String tmpdir;	              // directory for temporary files
    ClassLoader classLoader;          // global class loader to use
    int n_vncserverHostDisplays=0;    // number of vnc servers for login
    String[] vncserverHosts;          // individual vnc hosts
    int[] vncserverDisplays;          // individual vnc display numbers
    int localVncPortStart=5909;       // value of vnc port opened on last login
    // note that localVncPort should reflect the value in effect when the last
    // login terminal was opened, so startViewer() uses the right one even
    // if user changes the port after opening last login terminal.

    static final String BUNDLED_PUTTY_WINEXE= "(putty from SXRS_winexe.jar)";
    // ^= constant used to indicate bundled putty.exe
    static final String BUNDLED_VNCVIEWER_WINEXE= "(vncviewer from SXRS_winexe.jar)";
    // ^= constant used to indicate bundled vncviewer.exe
    static final String BUNDLED_VNCVIEWER_JAVA= "(pure Java VncViewer)";
    // ^= constant used to indicate bundled vncviewer.exe
    static final String MANUAL_VNCVIEWER_PLEASE= "(please start manually)";
    // ^= constant used to indicate manual VNC is preferred

    
    ////////////////////////////////////////////////////////////////
    // Applet methods
    public void init() {
	// preferences store for persistent user prefereces
	try {
	    myprefs= Preferences.userRoot().node("SXRS");
	}
	catch (Exception e) { myprefs= null; }

	// os
	os="unknown";
	try { os= System.getProperty("os.name");}
	catch (Exception e) {}

	// tmpdir
	tmpdir= ".";
	try {
	    String subdir="/SXRS_Files";
	    tmpdir= System.getProperty("java.io.tmpdir");
	    File tf= new File(tmpdir+subdir);
	    if ( tf.isDirectory() || tf.mkdirs() )
		tmpdir= tmpdir+subdir;
	}
	catch (Exception e) {	    
	}

	// class loader
	classLoader= this.getClass().getClassLoader();
	if (classLoader==null)
	    classLoader= ClassLoader.getSystemClassLoader();
	
	// set up all our widgets
	this.setLayout(new BorderLayout());
	
	// icon on west
	ImageIcon sxrs_icon;
	sxrs_icon= new ImageIcon
	    (classLoader.getResource("images/SXRS-icon.gif"),
	     "Secure eXperiment logo");
	add(new JLabel(sxrs_icon), BorderLayout.WEST);
	
	// input frame on east
	Box f1= new Box(BoxLayout.Y_AXIS);
	f1.add( Box.createGlue());

	// userId
	tf_userId= new TextField(10);
	userId= null;
	try {
	    try { userId=System.getProperty("user.name"); }
	    catch (Exception e) {}
	    try { if (myprefs != null) userId= myprefs.get("userId", userId); }
	    catch (Exception e) {}
	} catch (Exception e) { userId="your_userid"; }
	tf_userId.setText( userId );
	f1.add( makeInputPanel("Your userid:", tf_userId) );

	// viewonly option
	ckb_viewonly= new JCheckBox();
	f1.add( makeInputPanel("Start VNC in \"view only\" mode:", ckb_viewonly) );

	// advanced setting panel in this box
	pnl_advancedSettings= new Box(BoxLayout.Y_AXIS);
	//pnl_advancedSettings.setBorder(new BevelBorder(BevelBorder.LOWERED));

	// loginChain
	tf_loginChain= new TextField
	    ( getSystemProperty
	      ( "sxrs.loginChain", "( loginChain not set )" ), 30);
	pnl_advancedSettings.add( makeInputPanel("Login chain:", tf_loginChain));

	// vncserverHostDisplays
	cb_vncserverHostDisplays= new JComboBox( );
	String default_vncservers= getSystemProperty("sxrs.vncserverHostDisplay","");
	cb_vncserverHostDisplays.addItem(default_vncservers);
	for (int i=1; i<100; i++) {
	    String alt= getSystemProperty("sxrs.vncserverHostDisplay_"+i,"");
	    if (alt.length()<=0)
		break;
	    cb_vncserverHostDisplays.addItem( alt );
	}
	cb_vncserverHostDisplays.setSelectedItem(default_vncservers);
	cb_vncserverHostDisplays.setEditable(true);
	pnl_advancedSettings.add( makeInputPanel("VNC server host:display [...]:",
						 cb_vncserverHostDisplays));

	// localVncPort
	tf_localVncPortStart= new TextField( getSystemProperty("sxrs.localVncPort","5909") );
	pnl_advancedSettings.add( makeInputPanel("Local VNC port:",
						 tf_localVncPortStart));
	
	// localLoginPortStart
	tf_localLoginPortStart= new TextField( getSystemProperty("sxrs.localLoginPortStart","10022") );
	pnl_advancedSettings.add( makeInputPanel("Local login port start:",
						 tf_localLoginPortStart));

	// xtermCommandName
	String default_xtermCommandName= "xterm";
	if ( os.indexOf("Windows")>=0 )
	    default_xtermCommandName= BUNDLED_PUTTY_WINEXE;
	if ( os.indexOf("Mac")>=0 )
	    default_xtermCommandName= "/usr/X11R6/bin/xterm -display :0";
	String xtermCommandName= getSystemProperty("sxrs.xtermCommandName", default_xtermCommandName);
	cb_xtermCommandName= new JComboBox();
	cb_xtermCommandName.addItem(xtermCommandName);
	if ( !xtermCommandName.equals(default_xtermCommandName) )
	    cb_xtermCommandName.addItem(default_xtermCommandName);
	cb_xtermCommandName.setSelectedItem(xtermCommandName);
	cb_xtermCommandName.setEditable(true);
	pnl_advancedSettings.add( makeInputPanel("xterm command name:",
						 cb_xtermCommandName));

	// vncviewerCommandName
	String default_vncviewerCommandName= "vncviewer";
	if ( os.indexOf("Windows")>=0 )
	    default_vncviewerCommandName= BUNDLED_VNCVIEWER_WINEXE;
	if ( os.indexOf("Mac")>=0 )
	    default_vncviewerCommandName= BUNDLED_VNCVIEWER_JAVA;
	String vncviewerCommandName= getSystemProperty("sxrs.vncviewerCommandName", default_vncviewerCommandName);
	cb_vncviewerCommandName= new JComboBox();
	cb_vncviewerCommandName.addItem(vncviewerCommandName);
	if ( !vncviewerCommandName.equals(default_vncviewerCommandName) )
	    cb_vncviewerCommandName.addItem(default_vncviewerCommandName);
	if ( !default_vncviewerCommandName.equals(BUNDLED_VNCVIEWER_JAVA) )
	    cb_vncviewerCommandName.addItem(BUNDLED_VNCVIEWER_JAVA);
	cb_vncviewerCommandName.setSelectedItem(vncviewerCommandName);
	cb_vncviewerCommandName.setEditable(true);
	pnl_advancedSettings.add( makeInputPanel("vncviewer command name:",
						 cb_vncviewerCommandName));

	pnl_advancedSettings.setVisible( false );
	f1.add( pnl_advancedSettings );

	b_advancedSettingsToggle= new Button("Show advanced settings");
	f1.add( b_advancedSettingsToggle );

	add(f1, BorderLayout.EAST);

	// button frame on bottom
	Box f2= new Box(BoxLayout.X_AXIS);
	// start button box
	pnl_startTerminalButtons= new Box(BoxLayout.X_AXIS);
	f2.add(pnl_startTerminalButtons);
	updateFromLoginChain();
	// start vnc button
	pnl_startViewerButtons= new Box(BoxLayout.X_AXIS);
	f2.add(pnl_startViewerButtons);
	updateFromVncserverHostDisplays();
	// help button
	b_help= new Button("Help!");
	f2.add(b_help);
	add(f2, BorderLayout.SOUTH);

	// help pane -- invisible until called for
	JEditorPane helpText= new JEditorPane(  );
	helpText.setContentType("text/html");
	helpText.setEditable(false);
	try {
	    helpText.setPage( classLoader.getResource("html/helpJavaSXRS.html") );
	} catch (Exception e) {
	    helpText.setText("<H1>Error loading help!</H1>"+
		  "The help text resource could not be found.<P>"+e+
		  "This should never happen if you are running from a valid jar file." );
	}
	JScrollPane helpObject= new JScrollPane(helpText);
	helpFrame= new JFrame("Help for Secure eXperiment Remote Shift helper");
	helpFrame.getContentPane().add(helpObject); // 1.4 compatible
	helpFrame.setSize(500,250);

	// attach ActionListeners
	tf_loginChain.addActionListener(this);
	tf_userId.addActionListener(this);
	b_help.addActionListener(this);
	b_advancedSettingsToggle.addActionListener(this);
    }

    Panel makeInputPanel( String prompt, Component tf )
    {
	Panel panel= new Panel();
	panel.add( new Label(prompt) );
	panel.add( tf );
	return panel;
    }

    Panel makeRadioButtonPanel( String prompt,
				ActionListener alistener,
				JRadioButton [] jarr )
    {
	Panel panel= new Panel();
	panel.add( new Label(prompt) );
	ButtonGroup group= new ButtonGroup();
	for (int i=0; i<jarr.length; i++) {
	    group.add( jarr[i] );
	    panel.add( jarr[i] );
	    //	    jarr[i].addActionListener( alistener );
	}
	return panel;
    }

    void validateUserId() {
     	// get userId and check for validity
     	userId= tf_userId.getText();
     	if (userId.length()<=0 || userId.indexOf(' ')>=0) {
     	    JOptionPane.showMessageDialog
     		( this,
     		  "User ID must not contain spaces and must not be null.\n"
     		  + "Please enter a valid user ID and try again.",
     		  "Error",
     		  JOptionPane.ERROR_MESSAGE );
     	    return;
     	}

    }

    void updateFromLoginChain() {
	int nh=0;
	int i1,i2,i3;
	String s= tf_loginChain.getText();
	int l= s.length();
	// first count number of '!' to determine nh
	nh=1;
	for (i1=0; i1<l; i1++)
	    if (s.charAt(i1)=='!')
		nh++;
	// [re]initialize arrays iff size changes (otherwise re-use)
	if (nh != n_loginHosts) {
	    n_loginHosts= nh;
	    loginHosts= new String[nh];
	    loginUsers= new String[nh];
	    loginOptions= new String[nh];
	    loginPorts= new int[nh];
	    ba_startTerminal= new Button[nh];
	    pnl_startTerminalButtons.removeAll();
	    for (int ih=0; ih<nh; ih++) {
		ba_startTerminal[ih]= new Button("SSH #"+(ih+1));
		if (ih>0)
		    ba_startTerminal[ih].setEnabled(false);
		ba_startTerminal[ih].addActionListener(this);
		pnl_startTerminalButtons.add(ba_startTerminal[ih]);
	    }
	    this.validate();
	}
	
	validateUserId();
	int ih= 0;
	i1=i2=0;
	while (ih < nh && i1<l) {
	    // extract [user@]host
	    while (i1<l && s.charAt(i1)==' ')
	        i1++;
	    i2=i1;
	    while (i2<l && s.charAt(i2)!='!' && s.charAt(i2)!=' ')
		i2++;
	    // copy user and host
	    i3= s.indexOf('@',i1);
	    if (i3>i1 && i3<i2) {
		loginUsers[ih]= s.substring(i1,i3);
		loginHosts[ih]= s.substring(i3+1,i2);
	    }
	    else {
		loginUsers[ih]= userId;
		loginHosts[ih]= s.substring(i1,i2);
	    }
	    //System.out.println("host "+ih+" "+loginHosts[ih]);
	    //System.out.println("user "+ih+" "+loginUsers[ih]);
	    // extract options, if any
	    i1=i2;
	    while (i2<l && s.charAt(i2)!='!')
		i2++;
	    if (i2>i1)
		loginOptions[ih]= s.substring(i1,i2);
	    else
		loginOptions[ih]= "";
	    //System.out.println("option "+ih+" "+loginOptions[ih]);
	    ih++;
	    i1=i2+1;
	}
	if (ih != nh) {
	    System.err.println("Logic error in parsing option string! ih="+ih
			       +" nh="+nh);
	}
    }

    void updateFromVncserverHostDisplays()
    {
     	// get and validate vncserverHost and vncserverPort list,
	// update button panel
	String hds =((String)(cb_vncserverHostDisplays.getSelectedItem())).trim();
	int ihash= hds.indexOf("#");
	if (ihash > 0)
	    hds= hds.substring(0,ihash).trim();  // ignore comment
	StringTokenizer tk= new StringTokenizer(hds);
	int n= tk.countTokens();
	// [re]initialize arrays iff size changes (otherwise re-use)
	if (n != n_vncserverHostDisplays) {
	    n_vncserverHostDisplays= n;
	    vncserverHosts= new String[n];
	    vncserverDisplays= new int[n];
	    ba_startViewer= new Button[n];
	    pnl_startViewerButtons.removeAll();
	    for (int i=0; i<n; i++) {
		ba_startViewer[i]= new Button("VNC #"+(i+1));
		ba_startViewer[i].setEnabled(false);
		ba_startViewer[i].addActionListener(this);
		pnl_startViewerButtons.add(ba_startViewer[i]);
	    }
	    this.validate();
	}
	int i= 0;
	for (i=0; tk.hasMoreTokens() && i<n; i++) {
	    String hd= tk.nextToken();
	    String vncserverHost;
	    int vncserverDisplay= 1;
	    try {
		int icolon= hd.indexOf(":");
		if (icolon<0) {
		    vncserverHost= hd;
		}
		else {
		    vncserverHost= hd.substring(0,icolon);
		    vncserverDisplay= Integer.decode(hd.substring(icolon+1)).intValue();
		}
		vncserverHosts[i]= vncserverHost;
		vncserverDisplays[i]= vncserverDisplay;
	    }
	    catch (Exception e) {
		JOptionPane.showMessageDialog
		    ( this,
		      "Could not parse integer from vncserver Host:Display.\n"
		      +e,
		      "Error",
		      JOptionPane.ERROR_MESSAGE );
		return;
	    }
	}
    }

    ////////////////////////////////////////////////////////////////
    // utility methods

    String getSystemProperty( String propertyName, String defaultValue )
    {
	String rv=null;
	try {
	    if (myprefs != null)
		try { rv= myprefs.get(propertyName, null); } catch (Exception e) {}
	    if (rv==null)
		rv= System.getProperty(propertyName, defaultValue);
	    if (rv==null)
		return defaultValue;
	    else
		return rv;
	} catch (Exception e) {
	    return defaultValue;
	}
    }

    ////////////////////////////////////////////////////////////////
    // ActionListener methods for responding to events
    public void actionPerformed(ActionEvent evt) {
	Object source= evt.getSource();
	for (int ih=0; ih<n_loginHosts; ih++) {
	    if (source == ba_startTerminal[ih]) {
		// make sure buttons are up to date, hopefully not belatedly
		// (only a problem if the button user is hitting disappeared)
		updateFromLoginChain();
		updateFromVncserverHostDisplays();
		if (ih >= n_loginHosts) {
		    JOptionPane.showMessageDialog
			( this,
			  "It looks like you changed the login chain string but didn't hit return to update the button bar.\nPlease try again.",
			  "Oops.",
			  JOptionPane.ERROR_MESSAGE );
		    return;
		}
		TerminalRunner r= new TerminalRunner(this,ih);
		r.start();
		return;
	    }
	}
	for (int id=0; id<n_vncserverHostDisplays; id++) {
	    if (source == ba_startViewer[id]) {
		try {
		    startViewer(id);
		} catch (Exception e) {
		    JOptionPane.showMessageDialog
			( this,
			  "While trying to start vncviewer:\n"+e,
			  "Start vncviewer failure",
			  JOptionPane.ERROR_MESSAGE );		
		}
		return;
	    }
	}
	if (source == tf_userId) {
	    validateUserId();
	}
	else if (source == tf_loginChain) {
	    updateFromLoginChain();
	    updateFromVncserverHostDisplays();
	}
	else if (source == cb_vncserverHostDisplays) {
	    updateFromLoginChain();
	    updateFromVncserverHostDisplays();
	}
	else if (source == b_help) {
	    helpFrame.setVisible(true);
	    helpFrame.setState(helpFrame.NORMAL);
	}
	else if (source == b_advancedSettingsToggle) {
	    Container p= this.getParent();
	    if (p==null) p= this;
	    if (pnl_advancedSettings.isVisible()) {
		pnl_advancedSettings.setVisible(false);
		b_advancedSettingsToggle.setLabel("Show advanced settings");
	    }
	    else {
		pnl_advancedSettings.setVisible(true);
		b_advancedSettingsToggle.setLabel("Hide advanced settings");
	    }
	    updateFromLoginChain();
	    updateFromVncserverHostDisplays();
	    this.validate();
	    p.setSize( p.getPreferredSize() );
	    p.validate();
	}
	else {
	    JOptionPane.showMessageDialog
		( this,
		  "Unhandled action: "+evt+"\n"+
		  "Unimplemented widget, or actionListener was set for a widget that doesn't need it?",
		  "Harmless internal error",
		  JOptionPane.ERROR_MESSAGE );
	}
    }


    ////////////////////////////////////////////////////////////////
    // quit method for leaving when window closed
    public void quit()
    {
	System.exit(0);
    }

    ////////////////////////////////////////////////////////////////
    // WindowListener methods
    
    public void windowOpened(WindowEvent e) {}
    public void windowClosing(WindowEvent e) {
	quit();
    }
    public void windowClosed(WindowEvent e) {
	quit();
    }
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowActivated(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}

    ////////////////////////////////////////////////////////////////
    // main() so we can run standalone
    public static void main(String[] arg)
    {
	SXRS sxrs= new SXRS();

	sxrs.init();
	
	Frame f= new Frame("Secure eXperiment Remote Shift Client");
	f.add(sxrs);
	f.addWindowListener(sxrs);
	f.pack();
	f.setVisible(true);

	sxrs.start();
    }


// *+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*
// *+*  End of GUI code                                                  *+*
// *+*  Start of code to run external SXRS connection applications       *+*
// *+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*

  ////////////////////////////////////////////////////////////////
  // TerminalRunner embedded class
  class TerminalRunner extends Thread {
    SXRS appf;
    int istage;
      
    public TerminalRunner(SXRS arg_appf, int arg_istage) {
	appf=arg_appf;
	istage= arg_istage;
    }
      
    public void run()
    {
	try {
	    runTerminal();
	}
	catch (Exception e) {
	    JOptionPane.showMessageDialog
		( appf,
		  "While trying to start terminal:\n"+e,
		  "Start terminal failure",
		  JOptionPane.ERROR_MESSAGE );
	    e.printStackTrace();
	}
    }
      
    public void runTerminal() throws Exception
    {
	// set thisLoginPort
	int thisLoginPort=-1;
	if (istage>0)
	    thisLoginPort= loginPorts[istage-1];
	
	// set nextLoginPort
	int nextLoginPort;
     	try {
     	    nextLoginPort= Integer.decode(tf_localLoginPortStart.getText()).intValue() + istage;
     	}
     	catch (Exception e) {
     	    JOptionPane.showMessageDialog
     		( appf,
     		  "Could not parse integer from \"local login port\" advanced setting.\n"
     		  + "Please enter an integer and try again.",
     		  "Error",
     		  JOptionPane.ERROR_MESSAGE );
     	    return;
     	}
	loginPorts[istage]= nextLoginPort;
     
     	// get and check localVncPort
	// strictly only needed for last login, but check each time anyway
     	try {
     	    localVncPortStart= Integer.decode(tf_localVncPortStart.getText()).intValue();
     	}
     	catch (Exception e) {
     	    JOptionPane.showMessageDialog
     		( appf,
     		  "Could not parse integer from \"local VNC port\" advanced setting.\n"
     		  + "Please enter an integer and try again.",
     		  "Error",
     		  JOptionPane.ERROR_MESSAGE );
     	    return;
     	}
     
	// set nexthost
	String nexthost;
	if (istage < n_loginHosts-1)
	    nexthost= loginHosts[istage+1];
	else
	    nexthost= null;

	// set options
	String options;
	{
	    StringBuffer optionbuff= new StringBuffer();
	    optionbuff.append( loginOptions[istage]
			       + " -l " + loginUsers[istage] );
	    if (nexthost != null)
		optionbuff.append(" -L " + nextLoginPort + ":" + nexthost + ":22 ");
	    else {
		for (int id=0; id<n_vncserverHostDisplays; id++)
		    optionbuff.append
			( " -L " + (localVncPortStart+id) + ":"
			  + vncserverHosts[id] + ":"
			  + (vncserverDisplays[id]+5900) + " " );
	    }
	    options= optionbuff.toString();
	}
	
     	// get other settings
     	String xtermCommandName= (String)(cb_xtermCommandName.getSelectedItem());

     	// prepare command line for SSH process of choice
     	String[] command;
     	if ( xtermCommandName.indexOf("putty")<0 ) {
     	    // xterm and ssh must be on the command search path
     	    String scriptName= tmpdir+"/sxrs_ssh.bash";
     	    copyFromResource(null, "bash/sxrs_ssh.bash", scriptName);
	    String[] tmpcommand1= xtermCommandName.split(" ");
     	    String[] tmpcommand2=
     		{ "-e", "bash", scriptName,
		  options + ( istage > 0 ? " -p "+thisLoginPort+" localhost"
			      : loginHosts[0] ) };
	    command= new String[tmpcommand1.length + tmpcommand2.length];
	    for (int i=0; i<tmpcommand1.length; i++)
		command[i]= tmpcommand1[i];
	    for (int i=0; i<tmpcommand2.length; i++)
		command[i+tmpcommand1.length]= tmpcommand2[i];
     	}
     	else /* using putty */ {
     	    String exename= xtermCommandName;
     	    if ( exename.equals(BUNDLED_PUTTY_WINEXE) ) {
     		exename= tmpdir+"/sxrs_putty.exe";
		copyFromResource("SXRS_winexe.jar", "win_exe/putty.exe",
				 exename);
     	    }
	    String tmpcmd= exename + " -ssh -t " + options +
		(istage>0 ? " -P "+thisLoginPort+" localhost" : loginHosts[0]);
	    // note -P instead of -p for putty
     	    command= tmpcmd.split(" ");
	    // putty doesn't understand -Y, use -X -- a convenience for users
	    for (int i=0; i<command.length; i++)
		if (command[i].equals("-Y"))
		    command[i]= "-X";
     	}
     
     	// execute command
     	Runtime rt= Runtime.getRuntime();
     	Process p;
     	try {
     	    p= rt.exec( command );
     	    p.getOutputStream().close();  // this closes stdin of the process
     	    // (note "getOutputStream" gives the process's standard *input*)
     	} catch (Exception e) {
     	    StringBuffer cmdstring= new StringBuffer();
     	    for (int i=0; i<command.length; i++)
     		cmdstring.append(command[i]+" ");
     	    JOptionPane.showMessageDialog
     		( appf,
     		  "Execution of command failed! Command was\n"
     		  +cmdstring + "\nException: "+e+ "\nOs: "+os+
     		  (os.indexOf("Mac")>=0 ? "\n\n** Is X running? **" : ""),
     		  "Error",
     		  JOptionPane.ERROR_MESSAGE );
     	    return;
     	}
     
     	// if successful, enable the "startViewer" button
	if (istage < n_loginHosts-1)
	    ba_startTerminal[istage+1].setEnabled(true);
	else {
	    for (int i=0; i<n_vncserverHostDisplays; i++)
		if (ba_startViewer[i] != null)
		    ba_startViewer[i].setEnabled(true);
	}
     
     	// temporary...
     	//System.out.println("Created terminal process "+p+ "\nOs: "+os);
       	try {
       	    p.waitFor();
       	} catch(Exception e) {
       	    System.err.println("When waiting for process, had exception "+e);
       	}
     	try {
     	    InputStream is= p.getInputStream();
     	    InputStream es= p.getErrorStream();
     	    byte[] buffer= new byte[100];
     	    while (is.available()>0 || es.available()>0) {
     		int n;
     		if (es.available()>0)
     		    n= es.read(buffer);
     		else
     		    n= is.read(buffer);
     		if (n <= 0)
     		    break;
     		System.out.write(buffer,0,n);
     	    }
     	} catch(Exception e) {
     	    System.err.println("When reading from process, had exception "+e);
     	}
     	//System.out.println("terminal process ended with code "+p.exitValue());
     	// ... end temporary
     
     	int exitValue= p.exitValue();
     	if (exitValue != 0) {
     	    StringBuffer cmdstring= new StringBuffer();
     	    for (int i=0; i<command.length; i++)
     		cmdstring.append(command[i]+" ");
     	    JOptionPane.showMessageDialog
     		( appf,
     		  "Process ended with code "+exitValue
     		  + "\nCommand was\n"
     		  +cmdstring+ "\nOs: "+os+
     		  (os.indexOf("Mac")>=0 ? "\n\n** Is X running? **" : ""),
     		  "Error at terminal exit",
     		  JOptionPane.ERROR_MESSAGE );
     	}
     	
     	// save userId preference on success
     	if (exitValue==0 && myprefs != null)
     	    try {
     		myprefs.put("userId", userId);
     		myprefs.put("sxrs.xtermCommandName", xtermCommandName);
     	    }
     	    catch (Exception e) {}
    }
  }

    ////////////////////////////////////////////////////////////////
    // startViewer() method
    void startViewer(int id) throws Exception
    {
	// build and execute vncviewer command
	int localVncPort= localVncPortStart + id;
	String vncviewerCommandName= (String)cb_vncviewerCommandName.getSelectedItem();
	String exename= vncviewerCommandName;
	if ( vncviewerCommandName.equals(MANUAL_VNCVIEWER_PLEASE) ) {
	    String message = "On your OS, "+os
		+ ", it is recommended that you start your own vnc viewer.\n"
		+ "Please start your vncviewer using localhost for the host and 9 for the display.";
	    if ( ckb_viewonly.isSelected() )
		message = message + "\nNote: you will also have to select ViewOnly mode manually.";
	    JOptionPane.showMessageDialog
		( this,
		  message,
		  "Please start your VNCviewer now.",
		  JOptionPane.INFORMATION_MESSAGE );
	    return;
	}
	if ( vncviewerCommandName.equals(BUNDLED_VNCVIEWER_JAVA) ) {
	    // the following code is quite specific to TightVnc VncViewer class
	    VncViewer v = new VncViewer();
	    String [] args = { "HOST", "localhost", "PORT", ""+localVncPort,
			       "Share desktop", "Yes",
			       "Offer Relogin", "No",
			       "View only",
			       ckb_viewonly.isSelected() ? "Yes" : "No"};
	    v.mainArgs = args;
	    v.inAnApplet = false;
	    v.inSeparateFrame = true;
	    v.init();
	    v.inAnApplet = true;
	    v.start();
	    return;
	}
	if ( vncviewerCommandName.equals(BUNDLED_VNCVIEWER_WINEXE) ) {
	    exename= tmpdir+"/sxrs_vncviewer.exe";
	    copyFromResource("SXRS_winexe.jar", "win_exe/vncviewer.exe",
			     exename);
	}
	String command;
	if (! ckb_viewonly.isSelected() )
	    command= exename+" -Shared localhost:"+localVncPort;
	else
	    command= exename+" -Shared -ViewOnly localhost:"+localVncPort;

	Runtime rt= Runtime.getRuntime();
	Process p;
	try {
	    p= rt.exec( command );
	    p.getOutputStream().close();  // this closes stdin of the process
	    // (note "getOutputStream" gives the process's standard *input*)
	} catch (Exception e) {
	    JOptionPane.showMessageDialog
		( this,
		  "Execution of command failed! Command was\n"
		  +command + "\nException: "+e+ "\nOs: "+os,
		  "Error",
		  JOptionPane.ERROR_MESSAGE );
	    return;
	}
	if (myprefs != null)
	    try {
		myprefs.put("sxrs.vncviewerCommandName", vncviewerCommandName);
	    }
	    catch (Exception e) {}
    }

    ////////////////////////////////////////////////////////////////
    // copyFromResource() method for copying a file from a resource
    // (needed for copying external executable files)
    Vector resourcesCopied= new Vector();

    void copyFromResource(String resourceJar,
			  String resourceName,
			  String destFileName) throws Exception {
	String resourceId= resourceJar+":"+resourceName+":"+destFileName;
	if (resourcesCopied.contains(resourceId))
	    return;
	InputStream xi;
	if (resourceJar == null) {
	    xi= classLoader.getResourceAsStream( resourceName );
	    if (xi==null) {
		throw new Exception("Could not find resource "+resourceName);
	    }
	}
	else {
	    String resourceURL=null;
	    try {
		String base= classLoader.getResource("SXRS.class").toString();
		int ijar= base.lastIndexOf(".jar");
		int istart= base.lastIndexOf("/", ijar-1);
		resourceURL= base.substring(0,istart+1)+resourceJar+"!/"+resourceName;
		URL url= new URL(resourceURL);
		URLConnection conn= url.openConnection();
		xi= conn.getInputStream();
	    } catch (Exception e) {
		// try falling back on normal class loader
		xi= classLoader.getResourceAsStream( resourceName );
		if (xi==null) {
		    throw new Exception("Could not find resource "+resourceName
					+" in "+resourceJar
					+"\n ["+resourceURL+"]\n"+e);
		}
	    }
	}
	try {
	    FileOutputStream xo= new FileOutputStream( destFileName );
	    byte[]b= new byte[16384];
	    int ir=0;
	    while ( (ir=xi.read(b))>0 ) {
		xo.write(b,0,ir);
	    }
	    xo.close();
	    resourcesCopied.add(resourceId);
	} catch (Exception e) {
	    throw new Exception("Exception while attempting to copy resource "
			       +resourceName + " to file " +destFileName+":\n"
			       +e);
	}

    }
}
