package zpen;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.livescribe.penlet.Penlet;
import com.livescribe.penlet.RegionCollection;
import com.livescribe.display.AttributedText;
import com.livescribe.display.Display;
import com.livescribe.display.Transition;
import com.livescribe.storage.PenletStorage;
import com.livescribe.storage.StrokeStorage;
import com.livescribe.ui.MediaPlayer;
import com.livescribe.ui.ScrollLabel;
import com.livescribe.event.Event;
import com.livescribe.event.MenuEvent;
import com.livescribe.event.MenuEventListener;
import com.livescribe.event.PaperEvent;
import com.livescribe.event.StrokeListener;
import com.livescribe.event.PenTipListener;
import com.livescribe.event.HWRListener;
import com.livescribe.ext.ui.Menu;
import com.livescribe.icr.ICRContext;
import com.livescribe.icr.Resource;
import com.livescribe.penlet.Region;
import com.livescribe.afp.PageInstance;
import com.livescribe.geom.Rectangle;
import com.livescribe.geom.Stroke;

public class ZPen extends Penlet implements StrokeListener, HWRListener, PenTipListener, MenuEventListener {
    
    private Display display;
    private ScrollLabel label;
    private ICRContext icrContext;
    public boolean m_printedCredit;
	public Rectangle m_saveBounds;
	public Rectangle m_boxBeforeLastStroke;
	public int m_strokes;
	public int m_saveGameNum;
	public boolean m_displayingHelpText;
	public boolean m_ignoreNextStroke;
	private Menu rootMenu;
	private Menu currentMenu;
	private Menu playZork;
	private MediaPlayer player;
    
    ZOps zmachine;

    public ZPen() {   
    }

    /**
     * Invoked when the application is initialized.  This happens once for an application instance.
     */
    public void initApp() {
        this.display = this.context.getDisplay();
        this.label = new ScrollLabel();
        this.player = MediaPlayer.newInstance(this);
        createMenu();
		zmachine = new ZOps("/zork1.dat", this.logger);
    }
    
    /**
     * Invoked each time the penlet is activated.  Only one penlet is active at any given time.
     */
    public void activateApp(int reason, Object[] args) {
    	boolean bProblems=false;
    	
        this.context.addStrokeListener(this);
        
        // Configure the ICR context
        try {
            this.icrContext = this.context.getICRContext(1000, this);
            Resource[] resources = {
                this.icrContext.getDefaultAlphabetKnowledgeResource(),
                this.icrContext.createSKSystemResource(ICRContext.SYSRES_SK_ALPHA),
                this.icrContext.createAppResource("/icr/LEX_zork.res"),
                //this.icrContext.createLKSystemResource(ICRContext.SYSRES_LK_BIGRAM_30K),
                this.icrContext.createLKSystemResource(ICRContext.SYSRES_LK_GRAMMAR),
            };
            this.icrContext.addResourceSet(resources);            
        } catch (Exception e) {
            String msg = "Error initializing handwriting recognition resources: " + e.getMessage();
            this.logger.error(msg);
            this.label.draw(msg, true);
            this.display.setCurrent(this.label);
            bProblems=true;
        }
		context.addStrokeListener(this);
		context.addPenTipListener(this);

		m_saveGameNum=1;
		m_ignoreNextStroke=false;
		
		if (!bProblems)
		{
			if (reason!=ACTIVATED_BY_EVENT)
			{
				m_printedCredit=false;
				zmachine.executeUntilInputNeeded();
				label.draw(fixOutput(zmachine.getOutput()), true);
				showMenu(rootMenu, Transition.RIGHT_TO_LEFT);
			}
			else
			{
				// assuming we're woken up for restore event.
				zmachine.m_bNeedRestoring=true;
				currentMenu=playZork;
			}
		}
    }
    
    /**
     * Invoked when the application is deactivated.
     */
    public void deactivateApp(int reason) {
        this.context.removeStrokeListener(this);
        icrContext.dispose();
        icrContext = null;
		context.removeStrokeListener(this);            
		context.removePenTipListener(this);
    }
    
    /**
     * Invoked when the application is destroyed.  This happens once for an application instance.  
     * No other methods will be invoked on the instance after destroyApp is called.
     */
    public void destroyApp() {
    }


                 
    /**
     * Called when a new stroke is created on the pen. 
     * The stroke information is added to the ICRContext
     */
    public void strokeCreated(long time, Region regionId, PageInstance page) {
    	if (m_ignoreNextStroke) // double tap stroke event
    	{
    		m_ignoreNextStroke=false;
    		return;
    	}
		if (zmachine.m_bNeedSaving)
		{
			StrokeStorage strokeStorage = new StrokeStorage(page);
			Stroke stroke = strokeStorage.getStroke(time);
			Rectangle boundingBox=stroke.getBoundingBox();
			m_boxBeforeLastStroke=m_saveBounds;
			if (m_strokes==0)
				m_saveBounds=boundingBox;
			else
				m_saveBounds=Rectangle.getUnion(m_saveBounds, boundingBox);
			m_strokes++;
		}
		else if (zmachine.m_bNeedRestoring)
		{
			int areaId=regionId.getAreaId();
			if (areaId!=0)
			{
				try
				{
					PenletStorage ps = context.getInternalPenletStorage();
					InputStream is=ps.openInputStream(areaId+".sav");
					int size=is.available();
					byte[] store=new byte[size];
					is.read(store);
					is.close();
					DataInputStream stream=new DataInputStream(new ByteArrayInputStream(store));
					zmachine.restoreGame(stream);
					stream.close();
					runGame();
				}
				catch (IOException ex)
				{
					label.draw("[Restore failed. Try another save or double tap to cancel restore.]", true);
					display.setCurrent(label);
				}
			}
			else
			{
				label.draw("[Tap on a valid save picture or double tap to cancel.]", true);
				display.setCurrent(label);
			}
		}
		else if (zmachine.m_bNeedToQuit)
		{
			context.notifyStateChange(false);
		}
		else
		{
        	this.icrContext.addStroke(page, time);
		}
    }
    
    public String fixOutput(String output)
    {
    	String fixedOutput=new String();
    	if (output.endsWith("\n\n>")) // strip of > prompt
    	{
    		output=output.substring(0, output.length()-3);
    	}
        for (int i=0; i<output.length(); i++)
        {
        	char c=output.charAt(i);
        	if (c=='\n')
        	{
				if (fixedOutput.length()!=0)
				{
        			fixedOutput+="   ";
				}
        	}
        	else if (c=='\r')
        	{
        		// ignore
        	}
        	else
        	{
        		fixedOutput+=c;
        	}
        	if (!m_printedCredit && fixedOutput.endsWith("Copyright (c) 1981, 1982, 1983 Infocom, Inc. All rights reserved."))
        	{
        		fixedOutput+="   Used with permission from Activision Publishing, Inc. All rights reserved.";
        		m_printedCredit=true;
        	}
        }
        return fixedOutput;
    }

	public void singleTap(long time, int x, int y)
	{
		if (zmachine.m_bNeedToQuit)
		{
			context.notifyStateChange(false);
		}
	}

	public void doubleTap(long time, int x, int y)
	{
		if (zmachine.m_bNeedSaving)
		{
			DataOutputStream stream=null;
			if (m_strokes > 1)
			{
				try
				{
					PenletStorage ps = context.getInternalPenletStorage();
					while (ps.exists(m_saveGameNum+".sav"))
						m_saveGameNum++;
					while (m_saveGameNum>65535)
						m_saveGameNum-=65535;
					stream=new DataOutputStream(ps.openOutputStream(m_saveGameNum+".sav"));
					RegionCollection regions = context.getCurrentRegionCollection();
					Region newRegion = new Region(m_saveGameNum, false);
					regions.addRegion(m_boxBeforeLastStroke, newRegion);
				}
				catch (IOException ex)
				{
					stream=null;
				}
			}
			if (zmachine.saveGame(stream))
			{
				m_saveGameNum++;
			}
			if (m_strokes <= 1)
			{
				zmachine.m_output += "[Too few lines in your picture]\n"; 
			}
			if (stream!=null)
			{
				try
				{
					stream.close();
				}
				catch (IOException ex)
				{
				}
			}
			m_ignoreNextStroke=true;
			runGame();
		}
		else if (zmachine.m_bNeedRestoring)
		{
			zmachine.restoreFailed();
			m_ignoreNextStroke=true;
			runGame();
		}
	}
		
	public boolean checkOtherStates()
	{
		if (zmachine.m_bNeedSaving)
		{
			return true;
		}
		else if (zmachine.m_bNeedRestoring)
		{
			return true;
		}
		else if (zmachine.m_bNeedToQuit)
		{
			return true;
		}
		return false;
	}
   
	public void runGame()
	{
        zmachine.executeUntilInputNeeded();
        String output=zmachine.getOutput();
		if (zmachine.m_bNeedSaving)
		{
			output+="\n[Draw a picture to represent your save. Double tap on it when you're done.]";
			m_strokes=0;
			m_saveBounds=new Rectangle();
			m_boxBeforeLastStroke=new Rectangle();
		}
		else if (zmachine.m_bNeedRestoring)
		{
			output+="\n[Tap one of your pictures to restore. Double tap to cancel.]";			
		}
		else if (zmachine.m_bNeedToQuit)
		{
			output+="\n[Game has ended. Tap to quit.]";
		}
		label.draw(fixOutput(output), true);
		display.setCurrent(label);
	}

    /**
     * When the user pauses (pause time specified by the wizard),
     * all strokes in the ICRContext are cleared
     */
    public void hwrUserPause(long time, String result) {
        this.icrContext.clearStrokes();
        m_displayingHelpText=false;
		if (checkOtherStates())
		{
			// in save/load so ignore
			return;
		}
		if (result.equalsIgnoreCase("help"))
		{
			label.draw("[Tap on the left on the Nav Plus to access the help menu.]", true);
			m_displayingHelpText=true;
			return;
		}
        zmachine.giveInput(result);
		runGame();
    }
    
    /**
     * When the ICR engine detects an acceptable series or strokes,
     * it prints the detected characters onto the smartpen display.
     */
    public void hwrResult(long time, String result) {
    }
    
    /**
     * Called when an error occurs during handwriting recognition 
     */
    public void hwrError(long time, String error) {}
    
    /**
     * Called when the user crosses out text
     */
    public void hwrCrossingOut(long time, String result) {}
    
    /**
     * Specifies that the penlet should respond to events
     * related to open paper
     */
    public boolean canProcessOpenPaperEvents () {
        return true;
    }

	public void penDown(long time, Region region, PageInstance page) {
		// TODO Auto-generated method stub
		if (!checkOtherStates())
		{
			this.label.draw("Reading Input...", false);
			if (currentMenu != playZork)
			{
				display.setCurrent(label);
				currentMenu = playZork;
			}
		}
	}

	public void penUp(long time, Region region, PageInstance page) {
		// TODO Auto-generated method stub
		
	}
	
	public void createMenu()
	{		
		Menu helpMenu = new Menu();
		helpMenu.add(AttributedText.parseEnriched("<b>Controls:</b>", null));
		helpMenu.add("Just write what you want to do.");
		helpMenu.add("Examples: \"go north\", \"look\", \"open the mailbox\", \"put sword in the case\" or \"look at map\".");
		helpMenu.add("The pen will try matching your input to a word the game understands. If you are having problems getting it to recognise a word then it might be a word the game doesn't understand.");
		helpMenu.add("If you are having problems getting the game to do what you want try using a different word or phrasing.");
		helpMenu.add(AttributedText.parseEnriched("<b>Saving and Loading:</b>", null));
		helpMenu.add("To save your game write \"save\". You'll be asked to draw a picture. Double tap on it when you're done. That's all there is to it.");
		helpMenu.add("To restore your game write \"restore\". You'll be asked to tap on one of your pictures. Do so and your game will be restored to the same point you saved.");
		helpMenu.add("If you aren't within the app, tapping on a saved picture will launch the app and restore the game automatically.");
		
		Menu creditsMenu = new Menu();
		creditsMenu.add(AttributedText.parseEnriched("<b>Z-Machine Interpreter and Livescribe support:</b> Charlie Cole (charlie@vyse.me.uk)", null));
		creditsMenu.add(AttributedText.parseEnriched("<b>Zork 1:</b> Written by Michael Berlyn and Marc Blank.", null));
		creditsMenu.add("Used with permission from Activision Publishing, Inc. All rights reserved.");
		creditsMenu.add(AttributedText.parseEnriched("<b>Special Thanks:</b>", null));
		creditsMenu.add("Scott Cutler: For persuading Activision to let me release this :)");
		creditsMenu.add("Donald Melanson (Engadget): For bringing my dull YouTube video to the attention of thousands.");
		creditsMenu.add("Infocom: For making such a fantastic series of games. If you like this I encourage you to try Planetfall, Sherlock or Hitchhikers");
		creditsMenu.add("Activision: For allowing me to distribute such a classic game.");
		creditsMenu.add("Graham Nelson: For writting the Z-machine specification.");
		creditsMenu.add("Joyce Morrell (Livescribe): For helping me get this app on the store.");
	
		playZork = new Menu();
		
		rootMenu = new Menu();
		rootMenu.add("Play Zork", playZork);
		rootMenu.add("Help", helpMenu);
		buildInvisiclueMenus(rootMenu);
		rootMenu.add("Credits", creditsMenu);
	}
	
	public void buildInvisiclueMenus(Menu root)
	{
		Menu clue1 = new Menu();
		root.add("Invisiclues (Hints)", clue1);
		clue1.add("[WARNING: Although it's really tempting to check the hints. These games are most fun if you try to solve the puzzles yourself, so only use this as a last resort.]");
		clue1.add("[Another chance to go back. Go on, give it another go solving it yourself :)]");
		{
			Menu clue2 = new Menu();
			clue1.add("Introduction", clue2);
			clue2.add("Invisiclues were originally sold (and in later versions built into the games) to provide players of Infocom games with hints and clues. They were originally printed in invisible ink thus Invisiclues.");
			clue2.add("If you're really stuck this menu contains the solution to every puzzle in the game (and a few red herrings).");
			clue2.add("Use them wisely.");
		}
		{
			Menu clue2 = new Menu();
			clue1.add("Above Ground", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("Where do I find a machete?", clue3);
				clue3.add("There is none. The game must have some limitations. You can't expect to walk to the nearest airport and fly to London to see the British Museum...");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I cross the mountains?", clue3);
				clue3.add("Play ZORK II.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I kill the songbird?", clue3);
				clue3.add("What a concept! You need a psychiatrist.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Is the nest useful for anything?", clue3);
				clue3.add("In China you might make bird's nest soup.");
				clue3.add("This is not China.");
				clue3.add("In other words, no.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I open the egg without damaging it?", clue3);
				clue3.add("You don't.");
				clue3.add("Have you tried saying OPEN EGG?");
				clue3.add("It takes a great deal of manual dexterity and the proper tools.");
				clue3.add("Someone else in the game can do it.");
				clue3.add("Only the Thief can open the egg. Give it to him or leave it underground where he will find it.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I fix the broken canary?", clue3);
				clue3.add("It is broken beyond repair.");
				clue3.add("No one can fix it. Really!");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Are the leaves useful for anything?", clue3);
				clue3.add("They're great for hiding gratings.");
				clue3.add("They can be taken, counted, or burned.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I open the grating?", clue3);
				clue3.add("You must unlock it.");
				clue3.add("You need the skeleton key.");
				clue3.add("It can be unlocked only from below.");
				clue3.add("The grating and key can be found in the Maze.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get off the roof of the house?", clue3);
				clue3.add("How did you get up there?");
				clue3.add("Someone from Infocom would love to hear how you did it.");
				clue3.add("This is one of those questions which was put in here for the sole purpose of teaching a lesson -- do not use the presence or absence of a question on a certain topic as an indication of what is important, and don't assume that long answers indicate important questions.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Once I have the canary in an undamaged state, what do I do with it?", clue3);
				clue3.add("Something is attracted to its singing.");
				clue3.add("It is also a treasure.");
				clue3.add("Try winding it in the forest.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get the brass bauble?", clue3);
				clue3.add("You must open the egg first.");
				clue3.add("See the previous question.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I open the front door?", clue3);
				clue3.add("It cannot be knocked down.");
				clue3.add("It cannot be destroyed.");
				clue3.add("It cannot be opened.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get into the house?", clue3);
				clue3.add("Have you checked all sides?");
				clue3.add("There's a window in the back which is partly open. Open it and climb through.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Can I eat the lunch?", clue3);
				clue3.add("Try it. Try the water, too. You can't be afraid to try anything in ZORK I (but it may make sense to SAVE your state first).");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get into the dungeons?", clue3);
				clue3.add("The entrance is in the house.");
				clue3.add("Trapdoors can be hidden.");
				clue3.add("Move the rug.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What is a grue?", clue3);
				clue3.add("Ask ZORK I.");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("The Cellar Area", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("Can the trapdoor be opened from below?", clue3);
				clue3.add("No. The only way to keep the trapdoor from closing behind you is to find another exit (other than the chimney, which is very limited).");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get up the ramp in the Cellar?", clue3);
				clue3.add("\"The ramp is too slippery to climb.\"");
				clue3.add("Is there a way to make it less slippery?");
				clue3.add("No. You won't ever get up the ramp.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I negotiate with the Troll?", clue3);
				clue3.add("Trolls tend not to be conversational. They require a much more direct approach.");
				clue3.add("You won't get past the Troll while he is conscious.");
				clue3.add("Kill him with the sword.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What do I do with the axe?", clue3);
				clue3.add("It can be used as a weapon, but isn't really necessary for anything.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Does the paint in the studio mean anything?", clue3);
				clue3.add("The artist was sloppy.");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("The Maze", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get through the Maze?", clue3);
				clue3.add("It is essential that you make a map of the Maze.");
				clue3.add("All ten directions are used: N, S, E, W, NE, NW, SE, SW, UP and DOWN.");
				clue3.add("Some passages lead back to the same room.");
				clue3.add("Rooms can be marked by dropping objects. (However, the Thief can be a pain.)");
				clue3.add("There are 22 rooms west of the Troll Room.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What do I do with the rusty knife?", clue3);
				clue3.add("If you had your sword when you took it, the pulse of blinding light should have served as a warning.");
				clue3.add("Try throwing the knife or attacking someone with it.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What do I do with the skeleton?", clue3);
				clue3.add("Let the dead rest in peace.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Can I use the broken lantern?", clue3);
				clue3.add("If you think it's useful, there's this bridge you might be interested in.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get past the Cyclops?", clue3);
				clue3.add("Fighting isn't always the answer.");
				clue3.add("There are two solutions. The alternate begins at F.");
				clue3.add("What happens if you hang around too long, or give something to the Cyclops?");
				clue3.add("He's hungry, isn't he?");
				clue3.add("Feed him the lunch and water.");
				clue3.add("Do you remember your mythology?");
				clue3.add("Take a very close look at the commandment in the black book.");
				clue3.add("The Cyclops is scared silly of the name of his father's nemesis, ODYSSEUS (first letter of each line in commandment -- hard to see on pen display). The Latin version of the name, ULYSSES, is also accepted.");
				clue3.add("For fun, try saying ODYSSEUS elsewhere.");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("The Round Room Area", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get the platinum bar?", clue3);
				clue3.add("There are actually two solutions.");
				clue3.add("What is causing the loud roar?");
				clue3.add("Is there a way to control the flow of water?");
				clue3.add("Solve the puzzle of the dam.");
				clue3.add("Does opening or closing the dam gates affect anything downstream?");
				clue3.add("Open the dam gates. Wait until the reservoir is empty, then close the gates. Take advantage of the silence in the Loud Room while the reservoir refills.");
				clue3.add("This solution to the Loud Room requires no object or information from elsewhere in the game.");
				clue3.add("The solution has something to do with the room's acoustics.");
				clue3.add("What happens whenever you say something?  >something?<  >>something?<<");
				clue3.add("Type ECHO.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I kill the rock?", clue3);
				clue3.add("How silly!");
				clue3.add("The term \"living rock\" is metaphorical, and should not be taken literally.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Is there anything special about the mirror?", clue3);
				clue3.add("Breaking it is not a good idea.");
				clue3.add("Looking into it can be fun.");
				clue3.add("Did you ever try touching or rubbing it?");
				clue3.add("There are two Mirror Rooms. Touching the mirror in one transports you to the other.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I enter Hades?", clue3);
				clue3.add("You must exorcise the evil spirits.");
				clue3.add("For a hint, turn the page in the black book.");
				clue3.add("It requires the bell, book and candles.");
				clue3.add("Ring the bell, light the candles, and read the black book.");
				clue3.add("The order in which you perform the ceremony is very important. Also, you must be holding the candles when you light them. Speed is of the essence, too -- don't waste any more time than is necessary between steps.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Can I get anywhere from the Dome Room?", clue3);
				clue3.add("Yes. It is likely that you have seen the necessary equipment.");
				clue3.add("It is found in the Attic.");
				clue3.add("Tie the rope to the railing.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Can I go up from the Torch Room?", clue3);
				clue3.add("No.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get out of the Temple area?", clue3);
				clue3.add("You'll never reach the rope.");
				clue3.add("You can leave from the altar end by going down, but \"you haven't a prayer of getting the coffin down that hole.\"");
				clue3.add("Or solve the puzzle of the granite walls.");
				clue3.add("The altar has magical powers. What is usually done at altars?");
				clue3.add("Try praying.");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("The Dam Area", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("How do I blow up the dam?", clue3);
				clue3.add("What a concept!");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How is the control panel operated?", clue3);
				clue3.add("You can turn the bolt.");
				clue3.add("You need the wrench.");
				clue3.add("You must activate the panel. (Green bubble lights up.)");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What is the green bubble for?", clue3);
				clue3.add("It indicates that the control panel is activated. Use the buttons in the Maintenance Room.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What do I do with the object which looks like a tube of toothpaste?", clue3);
				clue3.add("Read the tube.");
				clue3.add("Brushing your teeth with it is not sensible.");
				clue3.add("It doesn't oil the bolt well.");
				clue3.add("Gooey gunk like this is good for patching leaks in water pipes or boats.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What is the screwdriver for?", clue3);
				clue3.add("You'll know when the time comes.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What about Maintenance Room buttons?", clue3);
				clue3.add("Try them all. You should be able to find out.");
				clue3.add("The blue button causes a water pipe to burst.");
				clue3.add("The red button turns the lights on and off.");
				clue3.add("The yellow button activates the control panel at the dam. (The green bubble is now glowing.)");
				clue3.add("The brown bubble deactivates the control panel.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Can I stop the leak?", clue3);
				clue3.add("Yes, but not with your finger.");
				clue3.add("Isn't there some sort of glop you could apply?");
				clue3.add("Use the gunk in the tube.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What is the pile of plastic good for?", clue3);
				clue3.add("What is the valve for?");
				clue3.add("Did you try blowing into it?");
				clue3.add("You need the air pump, which is north of the Reservoir.");
				clue3.add("Solve the dam problem, or figure out the mirror.");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("Old Man River", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("Can the river be crossed?", clue3);
				clue3.add("Not without a boat.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What will placate the River God?", clue3);
				clue3.add("What have you tried to throw into the river?");
				clue3.add("There is no River God. Anything thrown in is lost forever.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get back from across the river?", clue3);
				clue3.add("If you launch the boat from Sandy Beach, you can cross the river to the west to White Cliffs South.");
				clue3.add("It is also possible to cross the rainbow.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I control the boat?", clue3);
				clue3.add("Read the label.");
				clue3.add("You can say BOARD (or GET IN), DISEMBARK (or GET OUT), LAUNCH, and LAND (or a direction towards a landing area). You can also let the current carry you.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I carry a pointy object onto the boat?", clue3);
				clue3.add("Pointy objects can puncture a plastic boat. You should not carry them on. Put them in the boat before boarding or put them into a container, such as the brown sack, first.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I go over the falls?", clue3);
				clue3.add("Just stay in the boat and wait.");
				clue3.add("Well, what did you expect?");
				clue3.add("\"I see no intelligence here.\"");
				clue3.add("By the way, have you ever taken a close look at the word ARAGAIN?");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What is the significance of the rainbow?", clue3);
				clue3.add("You can cross it and get the pot of gold.");
				clue3.add("You do not click your heels together three times while saying \"There's no place like home.\"");
				clue3.add("The description of one of the treasures, and the result of manipulating it properly were meant to be subtle hints.");
				clue3.add("Raise or wave the sceptre while standing at the end of the rainbow.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get through the crack in the Damp Cave?", clue3);
				clue3.add("\"It's too narrow for most insects.\"");
				clue3.add("You don't.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I turn myself into an insect?", clue3);
				clue3.add("Build a cocoon?");
				clue3.add("Not bloody likely.");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("The Coal Mine Area", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("What do I do about the bat?", clue3);
				clue3.add("It's a vampire bat.");
				clue3.add("Have you never watched an old horror movie?");
				clue3.add("Use the garlic.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get beyond the Smelly Room?", clue3);
				clue3.add("If your lantern battery is dead, forget it.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I find my way through the coal mine?", clue3);
				clue3.add("I would think you were an expert maze-mapper by now.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Is the basket on the chain useful?", clue3);
				clue3.add("Anything that complex in ZORK I is useful.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get through the narrow passage from the Timber Room?", clue3);
				clue3.add("\"You cannot fit through this passage with that load.\"");
				clue3.add("Did you try dropping everything?");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What source of light can I bring into the Drafty Room?", clue3);
				clue3.add("Matches.");
				clue3.add("(Well, no one said they would work in a draft.) You can't carry a light source in. There is another way.");
				clue3.add("Why might the room be drafty?");
				clue3.add("Did you ever wonder where the shaft with the basket led?");
				clue3.add("Objects, including light sources, can be placed in the basket. The basket can be lowered and raised.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What is the timber for?", clue3);
				clue3.add("It makes the room more interesting and the adventurer more confused.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I use the machine?", clue3);
				clue3.add("The switch description should remind you of something.");
				clue3.add("Try putting something inside and turning the machine on with the screwdriver. Have a dictionary handy.");
				clue3.add("You can make a diamond from coal.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What is meant by the \"Granite Wall\" in the Slide Room?", clue3);
				clue3.add("Evidently the ancient Zorkers did not have strong truth-in-advertising laws. Take nothing for granite.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Is the coal good for anything?", clue3);
				clue3.add("It is a source of carbon.");
				clue3.add("One of the most valuable gems is made of carbon.");
				clue3.add("Diamonds are pure carbon in crystalline form. They are created under tremendous heat and pressure.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Is the gas of any use?", clue3);
				clue3.add("It's great for blowing up dim-witted adventurers who wander into a coal mine with an open flame.");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("The Land Beyond the Chasm", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("How do I cross the chasm?", clue3);
				clue3.add("There's no bridge.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I build a bridge?", clue3);
				clue3.add("An interesting idea...");
				clue3.add("The timber might be useful.");
				clue3.add("But then again, maybe not.");
				clue3.add("A valiant attempt, but this is getting you nowhere.");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("General Questions", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("Why does the sword glow?", clue3);
				clue3.add("Elvish swords are magical, and glow with a blue light when dangers (particularly dangerous beings) are near.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What do I do about the Thief?", clue3);
				clue3.add("Discretion is the better part of valor.");
				clue3.add("You can almost always avoid a confrontation by walking away. Although you may be robbed, at least you won't be killed.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How many points are there in the game?", clue3);
				clue3.add("Any time you say QUIT, RESTART, or SCORE, this is pointed out.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get out of the dungeons?", clue3);
				clue3.add("There are six exits.");
				clue3.add("The chimney will allow you to carry one object at a time in addition to your lamp.");
				clue3.add("Once you find an exit other than the chimney, the trapdoor will not close behind you.");
				clue3.add("Probably the easiest exit (conceptually) is by way of the grating. You will probably come across the other three exits while solving some of the harder problems, but it is not necessary to find more than one to complete the game.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What is the significance of all the engravings?", clue3);
				clue3.add("The knowledgeable critic, I. Q. Roundhead, wrote a ten-volume study of the engravings of the ancient Zorkers. To make a long story short, he concluded that the Zorkers were very strange people.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I kill the Thief?", clue3);
				clue3.add("The Thief is a cunning and dangerous opponent, skilled in the martial arts. Novice Zorkers would do well to avoid him.");
				clue3.add("It is possible to distract him for one move by giving him something of value.");
				clue3.add("The nasty knife is a marginally more effective weapon to use against him.");
				clue3.add("As you gain in points, you become a better match.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How can I recharge my lamp?", clue3);
				clue3.add("What makes you think you can?");
				clue3.add("It is always best to conserve resources. You can prolong its life by turning it off whenever you can and using alternate light sources.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What happens when you die in ZORK I?", clue3);
				clue3.add("You may appear in the forest with your belongings scattered (valuables below ground, nonvaluables above).");
				clue3.add("You may wander as a spirit until you find a way to resurrect yourself.");
				clue3.add("ZORK I is as fair as baseball. Three strikes and you're out.");
				clue3.add("You become a spirit if you have visited a certain location before death.");
				clue3.add("The location is the altar in the South Temple.");
				clue3.add("Try praying at the altar.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Who is \"the Other Occupant?\"", clue3);
				clue3.add("\"He of the large bag.\"");
				clue3.add("The Thief, of course.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I go over the falls without killing myself?", clue3);
				clue3.add("Why not ask, \"How do I cut off my head without killing myself?\"");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Where is HELLO SAILOR useful?", clue3);
				clue3.add("Are you sure you want to know?");
				clue3.add("Absolutely certain?");
				clue3.add("To quote the black book, \"Oh ye who go about saying unto each: 'Hello Sailor': Dost thou know the magnitude of thy sin before the gods? ... Surely thou shalt repent of thy cunning.\"");
				clue3.add("Nowhere. (You were warned.)");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("More General Questions", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("Why do things move and disappear in the dungeon?", clue3);
				clue3.add("The Thief is constantly moving about.");
				clue3.add("There is a high probability that he will take valuable objects (except the gold coffin) which you have seen. There is a much lower probability that he will take a nonvaluable object (again, only if you have seen it), and he may later decide to drop it.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Where are the treasures the Thief took from me?", clue3);
				clue3.add("As he wanders about stealing things, he puts them in his bag. Whenever he stops in his Treasure Room, he drops off the valuables he has collected.");
				clue3.add("You can get the contents of the bag by defeating him in a fight.");
				clue3.add("The Treasure Room is guarded by the Cyclops.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What do I do with the stiletto?", clue3);
				clue3.add("Congratulations! Getting the stiletto is rare. If you keep it away from the Thief, he won't attack you.");
				clue3.add("It is a weapon, nothing more.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Who is the lean and hungry gentleman?", clue3);
				clue3.add("The Thief.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Where can I use the shovel?", clue3);
				clue3.add("It will dig only into very soft soil.");
				clue3.add("Try it in the sand.");
				clue3.add("The sand in the Sandy Cave is most promising.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Is there any significance to all the granite walls?", clue3);
				clue3.add("There are only two true granite walls.");
				clue3.add("While next to a real granite wall, you can transport yourself to the location of the other by saying the name of the room.");
				clue3.add("The two granite walls are in the Temple and the Treasure Room.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("What's the best image-caster?", clue3);
				clue3.add("What are you talking about?");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get into the Strange Passage?", clue3);
				clue3.add("This is not necessary to complete the game.");
				clue3.add("See the alternative Cyclops answer.");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("How do I get into the Stone Barrow?", clue3);
				clue3.add("You'll know when the time comes.");
				clue3.add("When you have all 350 points, you'll be able to enter the Barrow.");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("How Points are Scored.", clue2);
			{
				Menu clue3 = new Menu();
				clue2.add("Progress Points", clue3);
				clue3.add("(Use only as a last resort.)");
				clue3.add("You get 10 points for getting into the house, 25 for getting into the Cellar, 5 for getting past the Troll, 13 for getting to the Drafty Room, and 25 for getting to the Treasure Room.");
				clue3.add("These points plus all the treasure points make 350. When you have all 350 points, the twentieth treasure will appear in the case -- a map which leads (indirectly) to 400 more points (ZORK II).");
			}
			{
				Menu clue3 = new Menu();
				clue2.add("Treasures: Their Values and Locations", clue3);
				clue3.add("(Use only as a last resort.)");
				clue3.add("(The treasure will be listed, followed by the points for taking it, the points for putting it in the trophy case, then the place it is found.)");
				clue3.add("jewel-encrusted egg - 5 - 5 - in nest in tree");
				clue3.add("clockwork canary - 6 - 4 - in the egg");
				clue3.add("beautiful painting - 4 - 6 - Gallery");
				clue3.add("platinum bar - 10 - 5 - Loud Room");
				clue3.add("ivory torch - 14 - 6 - Torch Room");
				clue3.add("gold coffin - 10 - 15 - Egypt Room");
				clue3.add("Egyptian sceptre - 4 - 6 - in the coffin");
				clue3.add("trunk of jewels - 15 - 5 - Reservoir");
				clue3.add("crystal trident - 4 - 11 - Atlantis Room");
				clue3.add("jade figurine - 5 - 5 - Bat Room");
				clue3.add("sapphire bracelet - 5 - 5 - Gas Room");
				clue3.add("huge diamond - 10 - 10 - you create it");
				clue3.add("bag of coins - 10 - 5 - in the Maze");
				clue3.add("crystal skull - 10 - 10 - Land of Living Dead");
				clue3.add("jeweled scarab - 5 - 5 - buried in Sandy Cave");
				clue3.add("large emerald - 5 - 10 - in the buoy");
				clue3.add("silver chalice - 10 - 5 - Treasure Room");
				clue3.add("pot of gold - 10 - 10 - End of Rainbow");
				clue3.add("brass bauble - 1 - 1 - the songbird has it");
			}
		}
		{
			Menu clue2 = new Menu();
			clue1.add("For Your Amusement", clue2);
			clue2.add("(Read only after you've finished the game)");
			{
				Menu clue3 = new Menu();
				clue2.add("Have you ever:", clue3);
				clue3.add("...opened the grating from beneath while the leaves were still on it?");
				clue3.add("...tried swearing at ZORK I?");
				clue3.add("...waved the sceptre while standing on the rainbow?");
				clue3.add("...tried anything nasty with the bodies in Hades?");
				clue3.add("...burned the black book?");
				clue3.add("...damaged the painting?");
				clue3.add("...lit the candles with the torch?");
				clue3.add("...read the matchbook?");
				clue3.add("...tried to take yourself (or the Thief, Troll, or Cyclops)?");
				clue3.add("...tried cutting things with the knife or sword?");
				clue3.add("...poured water on something burning?");
				clue3.add("...said WAIT or SCORE while dead (as a spirit)?");
			}
		}
	}
	
	public void showMenu(Menu menu, Transition t)
	{
		currentMenu = menu;
		menu.show(display, t);
	}
	
	public boolean handleMenuEvent(MenuEvent event)
	{
		if (event.getId() == MenuEvent.MENU_LEFT)
		{
			if (currentMenu==rootMenu)
			{
				return false;
			}
		}
		else if (event.getId() == MenuEvent.MENU_RIGHT)
		{
			if (currentMenu==rootMenu && currentMenu.getFocusIndex()==0)
			{
				if (m_displayingHelpText)
				{
					label.draw("[Hope you found what you were looking for. What do you want to do now?]", true);
					m_displayingHelpText=false;
				}
				display.setCurrent(label);
				currentMenu=playZork;
				return true;
			}
		}
		currentMenu = currentMenu.handleMenuEvent(event, display, player);
		return true;
	}
}
