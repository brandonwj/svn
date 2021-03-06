package org.tn5250j;
/**
 * Title: tn5250J
 * Copyright:   Copyright (c) 2001
 * Company:
 * @author  Kenneth J. Pouncey
 * @version 0.5
 *
 * Description:
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; see the file COPYING.  If not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307 USA
 *
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.border.*;

import java.awt.font.*;
import java.awt.geom.*;
import java.text.*;
import java.util.*;
import java.awt.image.*;
import java.io.*;
import java.awt.print.*;
import java.awt.datatransfer.*;
import java.beans.*;
import org.tn5250j.tools.*;


public class Screen5250  implements PropertyChangeListener,TN5250jConstants {

   ScreenChar[] screen;
   private ScreenChar[] errorLine;
   private ScreenFields screenFields;
   private int errorLineNum;
   public Font font;
   private int lastAttr;
   private int lastRow;
   private int lastCol;
   private int lastPos;
   private int lenScreen;

   private GuiGraphicBuffer bi;

   private boolean keyboardLocked;
   private KeyStrokenizer strokenizer;
   private tnvt sessionVT;
   private int numRows = 0;
   private int numCols = 0;
   int fmWidth = 0;
   int fmHeight = 0;
   LineMetrics lm;
   Color colorBlue;
   Color colorWhite;
   Color colorRed;
   Color colorGreen;
   Color colorPink;
   Color colorYellow;
   Color colorBg;
   Color colorTurq;
   Color colorGUIField;
   Color colorCursor;
   Color colorSep;
   Color colorHexAttr;

   private boolean updateCursorLoc;

   private Rectangle2D cursor = new Rectangle2D.Float();
   private Rectangle2D tArea; // text area

   private Rectangle2D aArea; // all screen area
   private Rectangle2D cArea; // command line area
   private Rectangle2D sArea; // status area
   private Rectangle2D pArea; // position area (cursor etc..)
   private Rectangle2D mArea; // message area
   private Rectangle2D iArea; // insert indicator

   private char char0 = 0;
   private static final int initAttr = 32;
   private static final char initChar = 0;
   private boolean statusErrorCode;
   private boolean statusXSystem;
   private int top;
   private int left;
   private Rectangle workR = new Rectangle();
   private boolean colSepLine = false;
   private boolean cursorActive = false;
   private boolean insertMode = false;
   private boolean keyProcessed = false;
   private Rectangle dirty = new Rectangle();
   private Graphics2D g2d;
   private Graphics2D gg2d;
   private Point startPoint;
   private Point endPoint;
   private int crossHair = 0;
   private boolean messageLight = false;
	public int homePos = 0;
	public int saveHomePos = 0;
   private boolean keysBuffered;
   private String bufferedKeys;
   private boolean updateFont;

	public boolean pendingInsert = false;

   private Gui5250 gui;
   private Properties appProps = null;
   private int cursorSize = 0;
   private boolean hotSpots = false;
   private boolean showHex = false;
   private float sfh = 1.2f;  // font scale height
   private float sfw = 1.0f;  // font scale height
   private float ps132 = 0;  // Font point size

   public final static byte STATUS_SYSTEM       = 1;
   public final static byte STATUS_ERROR_CODE   = 2;
   public final static byte STATUS_VALUE_ON     = 1;
   public final static byte STATUS_VALUE_OFF    = 2;

   private final static String xSystem = "X - System";
   private final static String xError = "X - II";
   private String statusString = "";
   private StringBuffer hsMore = new StringBuffer("More...");
   private StringBuffer hsBottom = new StringBuffer("Bottom");

   // error codes to be sent to the host on an error
   private final static int ERR_CURSOR_PROTECTED      = 0x05;
   private final static int ERR_INVALID_SIGN          = 0x11;
   private final static int ERR_NO_ROOM_INSERT        = 0x12;
   private final static int ERR_NUMERIC_ONLY          = 0x09;
   private final static int ERR_NUMERIC_09            = 0x10;
   private final static int ERR_FIELD_MINUS           = 0x16;

   private boolean guiInterface = false;
   protected boolean guiShowUnderline = true;

   public Screen5250(Gui5250 gui, Properties props) {

      this.gui = gui;

      loadProps(props);

      try {
         jbInit();
      }
      catch(Exception ex) {
         ex.printStackTrace();
      }
   }

   void jbInit() throws Exception {

      if (!appProps.containsKey("font"))
         font = new Font("dialoginput",Font.BOLD,14);
      else {
         font = new Font(getStringProperty("font"),Font.PLAIN,14);
      }

      gui.setFont(font);

      lastAttr = 32;

      // default number of rows and columns
      numRows = 24;
      numCols = 80;

      goto_XY(1,1);  // set initial cursor position

      errorLineNum = numRows;
      updateCursorLoc = false;
      FontRenderContext frc = new FontRenderContext(font.getTransform(),true,true);
      lm = font.getLineMetrics("Wy",frc);
      fmWidth = (int)font.getStringBounds("W",frc).getWidth() + 1;
      fmHeight = (int)(font.getStringBounds("g",frc).getHeight() +
                     lm.getDescent() + lm.getLeading());

      keyboardLocked = true;

      checkOffScreenImage();
      lenScreen = numRows * numCols;
      screen = new ScreenChar[lenScreen];
      for (int y = 0;y < lenScreen; y++) {
         screen[y] = new ScreenChar(this);
         screen[y].setCharAndAttr(' ',initAttr,false);
         screen[y].setRowCol(getRow(y),getCol(y));
      }

      screenFields = new ScreenFields(this);
      strokenizer = new KeyStrokenizer();

   }

   public final void setRowsCols(int rows, int cols) {

      // default number of rows and columns
      numRows = rows;
      numCols = cols;

      lenScreen = numRows * numCols;

      screen = new ScreenChar[lenScreen];
      for (int y = 0;y < lenScreen; y++) {
         screen[y] = new ScreenChar(this);
         screen[y].setCharAndAttr(' ',initAttr,false);
         screen[y].setRowCol(getRow(y),getCol(y));
      }
      errorLineNum = numRows;

      Rectangle r = gui.getDrawingBounds();
      resizeScreenArea(r.width,r.height);
      gui.repaint();
   }

   public void loadProps(Properties props) {

      appProps = props;
      loadColors();

      if (appProps.containsKey("colSeparator")) {
         if (getStringProperty("colSeparator").equals("Line"))
            colSepLine = true;
      }

      if (appProps.containsKey("showAttr")) {
         if (getStringProperty("showAttr").equals("Hex"))
            showHex = true;
      }

      if (appProps.containsKey("guiInterface")) {
         if (getStringProperty("guiInterface").equals("Yes"))
            guiInterface = true;
         else
            guiInterface = false;
      }

      if (appProps.containsKey("guiShowUnderline")) {
         if (getStringProperty("guiShowUnderline").equals("Yes"))
            guiShowUnderline = true;
         else
            guiShowUnderline = false;
      }

      if (appProps.containsKey("hotspots")) {
         if (getStringProperty("hotspots").equals("Yes"))
            hotSpots = true;
         else
            hotSpots = false;
      }

      if (appProps.containsKey("hsMore")) {
         if (getStringProperty("hsMore").length() > 0) {
            hsMore.setLength(0);
            hsMore.append(getStringProperty("hsMore"));
         }
      }

      if (appProps.containsKey("hsBottom")) {
         if (getStringProperty("hsBottom").length() > 0) {
            hsBottom.setLength(0);
            hsBottom.append(getStringProperty("hsBottom"));
         }
      }

      if (appProps.containsKey("colSeparator")) {
         if (getStringProperty("colSeparator").equals("Line"))
            colSepLine = true;
      }

      if (appProps.containsKey("cursorSize")) {
         if (getStringProperty("cursorSize").equals("Full"))
            cursorSize = 2;
         if (getStringProperty("cursorSize").equals("Half"))
            cursorSize = 1;
         if (getStringProperty("cursorSize").equals("Line"))
            cursorSize = 0;

      }

      if (appProps.containsKey("crossHair")) {
         if (getStringProperty("crossHair").equals("None"))
            crossHair = 0;
         if (getStringProperty("crossHair").equals("Horz"))
            crossHair = 1;
         if (getStringProperty("crossHair").equals("Vert"))
            crossHair = 2;
         if (getStringProperty("crossHair").equals("Both"))
            crossHair = 3;

      }

      if (appProps.containsKey("fontScaleHeight")) {
         sfh = getFloatProperty("fontScaleHeight");
      }

      if (appProps.containsKey("fontScaleWidth")) {
         sfw = getFloatProperty("fontScaleWidth");
      }

      if (appProps.containsKey("fontPointSize")) {
         ps132 = getFloatProperty("fontPointSize");
      }

   }

   protected final void loadColors() {

      colorBlue = new Color(140,120,255);
      colorTurq = new Color(0,240,255);
      colorRed = Color.red;
      colorWhite = Color.white;
      colorYellow = Color.yellow;
      colorGreen = Color.green;
      colorPink = Color.magenta;
      colorGUIField = Color.white;
      colorSep = Color.white;
      colorHexAttr = Color.white;

      if (guiInterface)
         colorBg = Color.lightGray;
      else
         colorBg = Color.black;

      colorCursor = Color.white;


      if (!appProps.containsKey("colorBg"))
         setProperty("colorBg",Integer.toString(colorBg.getRGB()));
      else {
         colorBg = getColorProperty("colorBg");
      }
      gui.setBackground(colorBg);

      if (!appProps.containsKey("colorBlue"))
         setProperty("colorBlue",Integer.toString(colorBlue.getRGB()));
      else
         colorBlue = getColorProperty("colorBlue");

      if (!appProps.containsKey("colorTurq"))
         setProperty("colorTurq",Integer.toString(colorTurq.getRGB()));
      else
         colorTurq = getColorProperty("colorTurq");

      if (!appProps.containsKey("colorRed"))
         setProperty("colorRed",Integer.toString(colorRed.getRGB()));
      else
         colorRed = getColorProperty("colorRed");

      if (!appProps.containsKey("colorWhite"))
         setProperty("colorWhite",Integer.toString(colorWhite.getRGB()));
      else
         colorWhite = getColorProperty("colorWhite");

      if (!appProps.containsKey("colorYellow"))
         setProperty("colorYellow",Integer.toString(colorYellow.getRGB()));
      else
         colorYellow = getColorProperty("colorYellow");

      if (!appProps.containsKey("colorGreen"))
         setProperty("colorGreen",Integer.toString(colorGreen.getRGB()));
      else
         colorGreen = getColorProperty("colorGreen");

      if (!appProps.containsKey("colorPink"))
         setProperty("colorPink",Integer.toString(colorPink.getRGB()));
      else
         colorPink = getColorProperty("colorPink");

      if (!appProps.containsKey("colorGUIField"))
         setProperty("colorGUIField",Integer.toString(colorGUIField.getRGB()));
      else
         colorGUIField = getColorProperty("colorGUIField");

      if (!appProps.containsKey("colorCursor"))
         setProperty("colorCursor",Integer.toString(colorCursor.getRGB()));
      else
         colorCursor = getColorProperty("colorCursor");

      if (!appProps.containsKey("colorSep")) {
         colorSep = colorWhite;
         setProperty("colorSep",Integer.toString(colorSep.getRGB()));
      }
      else
         colorSep = getColorProperty("colorSep");

      if (!appProps.containsKey("colorHexAttr")) {
         colorHexAttr = colorWhite;
         setProperty("colorHexAttr",Integer.toString(colorHexAttr.getRGB()));
      }
      else
         colorHexAttr = getColorProperty("colorHexAttr");

   }

   protected final String getStringProperty(String prop) {

      return (String)appProps.get(prop);

   }

   protected final int getIntProperty(String prop) {

      return Integer.parseInt((String)appProps.get(prop));

   }

   protected final Color getColorProperty(String prop) {

      if (appProps.containsKey(prop)) {
         Color c = new Color(getIntProperty(prop));
         return c;
      }
      else
         return null;

   }

   protected final float getFloatProperty(String prop) {

      if (appProps.containsKey(prop)) {
         float f = Float.parseFloat((String)appProps.get(prop));
         return f;
      }
      else
         return 0.0f;

   }

   protected final void setProperty(String key, String val) {

      appProps.setProperty(key,val);

   }

   public void propertyChange(PropertyChangeEvent pce) {

      String pn = pce.getPropertyName();
      boolean resetAttr = false;

      if (pn.equals("colorBg")) {
         colorBg = (Color)pce.getNewValue();
         resetAttr = true;

      }

      if (pn.equals("colorBlue")) {
         colorBlue = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorTurq")) {
         colorTurq = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorRed")) {
         colorRed = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorWhite")) {
         colorWhite = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorYellow")) {
         colorYellow = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorGreen")) {
         colorGreen = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorPink")) {
         colorPink = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorGUIField")) {
         colorGUIField = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorCursor")) {
         colorCursor = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorSep")) {
         colorSep = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("colorHexAttr")) {
         colorHexAttr = (Color)pce.getNewValue();
         resetAttr = true;
      }

      if (pn.equals("cursorSize")) {
         if (pce.getNewValue().equals("Full"))
            cursorSize = 2;
         if (pce.getNewValue().equals("Half"))
            cursorSize = 1;
         if (pce.getNewValue().equals("Line"))
            cursorSize = 0;

      }

      if (pn.equals("crossHair")) {
         if (pce.getNewValue().equals("None"))
            crossHair = 0;
         if (pce.getNewValue().equals("Horz"))
            crossHair = 1;
         if (pce.getNewValue().equals("Vert"))
            crossHair = 2;
         if (pce.getNewValue().equals("Both"))
            crossHair = 3;

      }

      if (pn.equals("colSeparator")) {
         if (pce.getNewValue().equals("Line"))
            colSepLine = true;
         else
            colSepLine= false;
      }

      if (pn.equals("showAttr")) {
         if (pce.getNewValue().equals("Hex"))
            showHex = true;
         else
            showHex= false;
      }

      if (pn.equals("guiInterface")) {
         if (pce.getNewValue().equals("Yes"))
            guiInterface = true;
         else
            guiInterface = false;
      }

      if (pn.equals("guiShowUnderline")) {
         if (pce.getNewValue().equals("Yes"))
            guiShowUnderline = true;
         else
            guiShowUnderline = false;
      }

      if (pn.equals("hotspots")) {
         if (pce.getNewValue().equals("Yes"))
            hotSpots = true;
         else
            hotSpots = false;
      }

      if (pn.equals("hsMore")) {
         hsMore.setLength(0);
         hsMore.append((String)pce.getNewValue());

      }

      if (pn.equals("hsBottom")) {
         hsBottom.setLength(0);
         hsBottom.append((String)pce.getNewValue());

      }

      if (pn.equals("font")) {
         font = new Font((String)pce.getNewValue(),Font.PLAIN,14);
         updateFont = true;
      }

      if (pn.equals("fontScaleHeight")) {

//         try {
            sfh = Float.parseFloat((String)pce.getNewValue());
            updateFont = true;
//         }

      }

      if (pn.equals("fontScaleWidth")) {

//         try {
            sfw = Float.parseFloat((String)pce.getNewValue());
            updateFont = true;
//         }

      }

      if (pn.equals("fontPointSize")) {

//         try {
            ps132 = Float.parseFloat((String)pce.getNewValue());
            updateFont = true;
//         }

      }

      if (updateFont) {
         Rectangle r = gui.getDrawingBounds();
         resizeScreenArea(r.width,r.height);
         updateFont = false;
      }

      if (resetAttr)
         for (int y = 0;y < lenScreen; y++) {
            screen[y].setAttribute(screen[y].getCharAttr());
         }

      gui.repaint();
      gui.revalidate();
   }

   public boolean isHotSpots() {
      return hotSpots;
   }

   public void toggleHotSpots() {
      hotSpots = !hotSpots;
   }

   public void toggleGUIInterface() {
      guiInterface = !guiInterface;
   }

   /**
    *
    * RubberBanding start code
    *
    */

   public Point translateStart(Point start) {

      // because getRowColFromPoint returns position offset as 1,1 we need
      // to translate as offset 0,0
      int pos = getRowColFromPoint(start.x,start.y) - (numCols + 1);
      start.setLocation(screen[pos].x,screen[pos].y);
      return start;


   }

   public Point translateEnd(Point end) {

      // because getRowColFromPoint returns position offset as 1,1 we need
      // to translate as offset 0,0
      int pos = getRowColFromPoint(end.x,end.y) - (numCols + 1);

      int x = screen[pos].x + fmWidth - 1;
      int y = screen[pos].y + fmHeight - 1;

//      System.out.println(" ex = " + x + " sx = " + rubberband.getStartPoint().x);

      end.setLocation(x,y);

      return end;
   }

   public Color getBackground() {
      return colorBg;
   }

   /**
    *
    * RubberBanding end code
    *
    */

   /**
    *
    * Copy & Paste start code
    *     fix me
    */
   protected final void copyMe() {

      Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
      StringBuffer s = new StringBuffer();

      if (!gui.rubberband.isAreaSelected())
         workR.setBounds(0,0,numCols,numRows);
      else {
         // lets get the bounding area using a rectangle that we have already
         // allocated
         gui.rubberband.getBoundingArea(workR);

         // get starting row and column
         int sPos = getRowColFromPoint(workR.x ,
                                    workR.y );
         // get the width and height
         int ePos = getRowColFromPoint(workR.width ,
                                    workR.height );
         int r = getRow(sPos);
         int c = getCol(sPos);

         workR.setBounds(r,c,getCol(ePos),getRow(ePos));
         gui.rubberband.reset();
         gui.repaint();
      }

      System.out.println("Copying");
      System.out.println(workR);

      // loop through all the screen characters to send them to the clip board
      int m = workR.x;
      int i = 0;
      int t = 0;

      while (workR.height-- > 0) {
         t = workR.width;
         i = workR.y;
         while (t-- > 0) {
            // only copy printable characters (in this case >= ' ')
            char c = screen[getPos(m,i)].getChar();
            if (c >= ' ' && !screen[getPos(m,i)].nonDisplay)
               s.append(c);
            else
               s.append(' ');

            i++;
         }
         s.append('\n');
         m++;
      }
      StringSelection contents = new StringSelection(s.toString());
      cb.setContents(contents, null);

   }

   protected final void pasteMe(boolean special) {

      Clipboard cb =  Toolkit.getDefaultToolkit().getSystemClipboard();
      Transferable content = cb.getContents(this);
      setCursorOff();
      try {
         StringBuffer sb = new StringBuffer((String)content.getTransferData(DataFlavor.stringFlavor));
         StringBuffer pd = new StringBuffer();
         int r = getRow(lastPos);
         int nextChar = 0;
         int nChars = sb.length();
         boolean omitLF = false;
         boolean done = false;
         screenFields.saveCurrentField();
         int lr = getRow(lastPos);
         int lc = getCol(lastPos);
         resetDirty(lastPos);

         while (!done) {


            if (nextChar >= nChars) { /* EOF */

               done = true;
               break;
            }

            pd.setLength(0);

            boolean eol = false;
            char c = 0;
            int i;

            /* Skip a leftover '\n', if necessary */
            if (omitLF && (sb.charAt(nextChar) == '\n'))
               nextChar++;

            boolean skipLF = false;
            omitLF = false;

            charLoop:

            for (i = nextChar; i < nChars; i++) {
               c = sb.charAt(i);
               if ((c == '\n') || (c == '\r')) {
                  eol = true;
                  break charLoop;
               }
            }

            int startChar = nextChar;
            nextChar = i;

            pd.append(sb.substring(startChar, startChar + (i - startChar)));

            if (eol) {
               nextChar++;
               if (c == '\r') {
                  skipLF = true;
               }
            }
            System.out.println("pasted >" + pd + "<");

            int col = getCol(lastPos);
            int t = numCols - col;
            if (t > pd.length())
               t = pd.length();
            int p = 0;
            char pc;
            boolean setIt;
            while (t-- > 0) {

               pc = pd.charAt(p);
               setIt = true;
               if (special &&
                  (!Character.isLetter(pc) &&
                   !Character.isDigit(pc)))
                  setIt = false;

               if (isInField(r,col) && setIt) {
                  screen[getPos(r,col)].setChar(pc);
                  setDirty(r,col);
                  screenFields.setCurrentFieldMDT();
               }
               p++;
               if (setIt)
                  col++;
            }
            r++;

         }
         screenFields.restoreCurrentField();
         updateDirty();

         goto_XY(lr+1,lc+1);


      }
      catch (Throwable exc) {
         System.err.println(exc);
      }
      setCursorOn();
   }

   protected final void copyField(int pos) {

      Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
      StringBuffer s = new StringBuffer();
      screenFields.saveCurrentField();
      isInField(pos);
      System.out.println("Copying");
      StringSelection contents = new StringSelection(screenFields.getCurrentFieldText());
      cb.setContents(contents, null);
      screenFields.restoreCurrentField();
   }

   /**
    *
    * Copy & Paste end code
    *
    */

   /**
    * Sum them
    */
   protected final Vector sumThem(boolean which) {

      StringBuffer s = new StringBuffer();

      // lets get the bounding area using a rectangle that we have already
      // allocated
      gui.rubberband.getBoundingArea(workR);

      // get starting row and column
      int sPos = getRowColFromPoint(workR.x ,
                                 workR.y );
      // get the width and height
      int ePos = getRowColFromPoint(workR.width ,
                                 workR.height );
      int row = getRow(sPos);
      int col = getCol(sPos);

      workR.setBounds(row,col,getCol(ePos),getRow(ePos));
      gui.rubberband.reset();
      gui.repaint();

      System.out.println("Summing");
      System.out.println(workR);

      // loop through all the screen characters to send them to the clip board
      int m = workR.x;
      int i = 0;
      int t = 0;

      double sum = 0.0;

      // obtain the decimal format for parsing
      DecimalFormat df =
            (DecimalFormat)NumberFormat.getInstance() ;

      DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();

      if (which) {
         dfs.setDecimalSeparator('.');
         dfs.setGroupingSeparator(',');
      }
      else {
         dfs.setDecimalSeparator(',');
         dfs.setGroupingSeparator('.');
      }

      df.setDecimalFormatSymbols(dfs);

      Vector sumVector = new Vector();

      while (workR.height-- > 0) {
         t = workR.width;
         i = workR.y;
         while (t-- > 0) {

            // only copy printable numeric characters (in this case >= ' ')
            char c = screen[getPos(m,i)].getChar();
            if (((c >= '0' && c <= '9') || c== '.' || c == ',' || c == '-')
                                    && !screen[getPos(m,i)].nonDisplay) {
               s.append(c);
            }
            i++;
         }

         if (s.length() > 0) {
            if (s.charAt(s.length()-1) == '-') {
               s.insert(0,'-');
               s.deleteCharAt(s.length()-1);
            }
            try {
               Number n = df.parse(s.toString());
               System.out.println(s + " " + n.doubleValue());

               sumVector.add(new Double(n.doubleValue()));
               sum += n.doubleValue();
            }
            catch (ParseException pe) {
               System.out.println(pe.getMessage() + " at " + pe.getErrorOffset());
            }
         }
         s.setLength(0);
         m++;
      }
//      System.out.println(sum);
      return sumVector;
   }

   public void moveCursor (MouseEvent e) {
      if(!keyboardLocked) {

         int pos = getRowColFromPoint(e.getX(),e.getY());
//         System.out.println((getRow(pos)) + "," + (getCol(pos)));
//         System.out.println(e.getX() + "," + e.getY()+ "," + fmWidth+ "," + fmHeight);
         if (pos == 0)
            return ;
         // because getRowColFromPoint returns offset of 1,1 we need to
         //    translate to offset 0,0
         pos -= (numCols + 1);

         int g = screen[pos].getWhichGUI();

         // lets check for hot spots
         if (g >= ScreenChar.BUTTON_LEFT &&  g <= ScreenChar.BUTTON_LAST) {
            StringBuffer aid = new StringBuffer();
            boolean aidFlag = true;
            switch (g) {
               case ScreenChar.BUTTON_RIGHT:
               case ScreenChar.BUTTON_MIDDLE:
                  while (screen[--pos].getWhichGUI() !=
                           ScreenChar.BUTTON_LEFT) {
                  }
               case ScreenChar.BUTTON_LEFT:
                  if (screen[pos].getChar() == 'F') {
                     pos++;
                  }
                  else
                     aidFlag=false;

                  if (screen[pos+1].getChar() != '=' &&
                        screen[pos+1].getChar() != '.' &&
                        screen[pos+1].getChar() != '/' ) {
//                     System.out.println(" Hotspot clicked!!! we will send characters " +
//                                    screen[pos].getChar() +
//                                    screen[pos+1].getChar());
                     aid.append(screen[pos].getChar());
                     aid.append(screen[pos + 1].getChar());
                  }
                  else {
//                        System.out.println(" Hotspot clicked!!! we will send character " +
//                                    screen[pos].getChar());
//
                     aid.append(screen[pos].getChar());
                  }
                  break;

            }
            if (aidFlag) {
               switch (g) {

                  case ScreenChar.BUTTON_LEFT_UP:
                  case ScreenChar.BUTTON_MIDDLE_UP:
                  case ScreenChar.BUTTON_RIGHT_UP:
                  case ScreenChar.BUTTON_ONE_UP:
                  case ScreenChar.BUTTON_SB_UP:
                  case ScreenChar.BUTTON_SB_GUIDE:
                     gui.sendAidKey(tnvt.AID_ROLL_UP);
                     break;

                  case ScreenChar.BUTTON_LEFT_DN:
                  case ScreenChar.BUTTON_MIDDLE_DN:
                  case ScreenChar.BUTTON_RIGHT_DN:
                  case ScreenChar.BUTTON_ONE_DN:
                  case ScreenChar.BUTTON_SB_DN:
                  case ScreenChar.BUTTON_SB_THUMB:

                     gui.sendAidKey(tnvt.AID_ROLL_DOWN);
                     break;
                  case ScreenChar.BUTTON_LEFT_EB:
                  case ScreenChar.BUTTON_MIDDLE_EB:
                  case ScreenChar.BUTTON_RIGHT_EB:
                     System.out.println("Send to external Browser");
                     break;

                  default:
                     int aidKey = Integer.parseInt(aid.toString());
                     if (aidKey >= 1 && aidKey <= 12)
                        gui.sendAidKey(0x30 + aidKey);
                     if (aidKey >= 13 && aidKey <= 24)
                        gui.sendAidKey(0xB0 + (aidKey - 12));
               }
            }
            else {
               if (screenFields.getCurrentField() != null) {
                  int xPos = screenFields.getCurrentField().startPos();
                  for (int x = 0; x < aid.length(); x++) {
//                  System.out.println(sr + "," + (sc + x) + " " + aid.charAt(x));
                     screen[xPos + x].setChar(aid.charAt(x));
                  }
//                  System.out.println(aid);
                  screenFields.setCurrentFieldMDT();
                  gui.sendAidKey(tnvt.AID_ENTER);
               }

            }
         }
         else {
            if (gui.rubberband.isAreaSelected()) {
               gui.rubberband.reset();
               gui.repaint();
            }

            goto_XY(pos);
            isInField(lastPos);
         }
      }
      gui.requestFocus();
   }

   // this returns the row column position with left, top starting at 1,1
   //    not 0,0
   public int getRowColFromPoint (int x, int y) {

      // is x,y in the drawing area
      // x is left to right and y is top to bottom
      if (tArea.contains(x,y)) {

//         int cols = (numCols - ((((fmWidth * numCols) - x) / fmWidth)));
//         System.out.println(cols);
         return getPos((numRows - ((((fmHeight * (numRows)) - y) / fmHeight))),
                        (numCols - ((((fmWidth * (numCols)) - x) / fmWidth)))
                     );

      }

      return 0;
   }

   // this returns the row column position with left, top starting at 1,1
   //    not 0,0
   public void getPointFromRowCol (int r, int c, Point point) {

      point.x = screen[getPos(r,c)].x;
      point.y = screen[getPos(r,c)].y;

   }

   protected void setVT(tnvt v) {

      sessionVT = v;
   }

   /**
    * Searches the mnemonicData array looking for the specified string.  If
    * it is found it will return the value associated from the mnemonicValue
    *
    * @see #sendKeys
    *
    */
   private int getMnemonicValue(String mnem) {

      for (int x = 0; x < mnemonicData.length; x++) {

         if (mnemonicData[x].equals(mnem))
            return mnemonicValue[x];
      }
      return 0;

   }

   /**
    * The sendKeys method sends a string of keys to the virtual screen. This
    * method acts as if keystrokes were being typed from the keyboard.  The
    * keystrokes will be sent to the location given. The string being passed can
    * also contain mnemonic values such as [enter] enter key,[tab] tab key,
    * [pf1] pf1 etc...
    *
    * These will be processed as if you had pressed these keys from the keyboard.
    * All the valid special key values are contained in the MNEMONIC
    * enumeration:
    *
    * <table BORDER COLS=2 WIDTH="50%" >
    *
    * <tr><td>MNEMONIC_CLEAR </td><td>[clear]</td></tr>
    * <tr><td>MNEMONIC_ENTER </td><td>[enter]</td></tr>
    * <tr><td>MNEMONIC_HELP </td><td>[help]</td></tr>
    * <tr><td>MNEMONIC_PAGE_DOWN </td><td>[pgdown]</td></tr>
    * <tr><td>MNEMONIC_PAGE_UP </td><td>[pgup]</td></tr>
    * <tr><td>MNEMONIC_PRINT </td><td>[print]</td></tr>
    * <tr><td>MNEMONIC_PF1 </td><td>[pf1]</td></tr>
    * <tr><td>MNEMONIC_PF2 </td><td>[pf2]</td></tr>
    * <tr><td>MNEMONIC_PF3 </td><td>[pf3]</td></tr>
    * <tr><td>MNEMONIC_PF4 </td><td>[pf4]</td></tr>
    * <tr><td>MNEMONIC_PF5 </td><td>[pf5]</td></tr>
    * <tr><td>MNEMONIC_PF6 </td><td>[pf6]</td></tr>
    * <tr><td>MNEMONIC_PF7 </td><td>[pf7]</td></tr>
    * <tr><td>MNEMONIC_PF8 </td><td>[pf8]</td></tr>
    * <tr><td>MNEMONIC_PF9 </td><td>[pf9]</td></tr>
    * <tr><td>MNEMONIC_PF10 </td><td>[pf10]</td></tr>
    * <tr><td>MNEMONIC_PF11 </td><td>[pf11]</td></tr>
    * <tr><td>MNEMONIC_PF12 </td><td>[pf12]</td></tr>
    * <tr><td>MNEMONIC_PF13 </td><td>[pf13]</td></tr>
    * <tr><td>MNEMONIC_PF14 </td><td>[pf14]</td></tr>
    * <tr><td>MNEMONIC_PF15 </td><td>[pf15]</td></tr>
    * <tr><td>MNEMONIC_PF16 </td><td>[pf16]</td></tr>
    * <tr><td>MNEMONIC_PF17 </td><td>[pf17]</td></tr>
    * <tr><td>MNEMONIC_PF18 </td><td>[pf18]</td></tr>
    * <tr><td>MNEMONIC_PF19 </td><td>[pf19]</td></tr>
    * <tr><td>MNEMONIC_PF20 </td><td>[pf20]</td></tr>
    * <tr><td>MNEMONIC_PF21 </td><td>[pf21]</td></tr>
    * <tr><td>MNEMONIC_PF22 </td><td>[pf22]</td></tr>
    * <tr><td>MNEMONIC_PF23 </td><td>[pf23]</td></tr>
    * <tr><td>MNEMONIC_PF24 </td><td>[pf24]</td></tr>
    * <tr><td>MNEMONIC_BACK_SPACE </td><td>[backspace]</td></tr>
    * <tr><td>MNEMONIC_BACK_TAB </td><td>[backtab]</td></tr>
    * <tr><td>MNEMONIC_UP </td><td>[up]</td></tr>
    * <tr><td>MNEMONIC_DOWN </td><td>[down]</td></tr>
    * <tr><td>MNEMONIC_LEFT </td><td>[left]</td></tr>
    * <tr><td>MNEMONIC_RIGHT </td><td>[right]</td></tr>
    * <tr><td>MNEMONIC_DELETE </td><td>[delete]</td></tr>
    * <tr><td>MNEMONIC_TAB </td><td>"[tab]</td></tr>
    * <tr><td>MNEMONIC_END_OF_FIELD </td><td>[eof]</td></tr>
    * <tr><td>MNEMONIC_ERASE_EOF </td><td>[eraseeof]</td></tr>
    * <tr><td>MNEMONIC_ERASE_FIELD </td><td>[erasefld]</td></tr>
    * <tr><td>MNEMONIC_INSERT </td><td>[insert]</td></tr>
    * <tr><td>MNEMONIC_HOME </td><td>[home]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD0 </td><td>[keypad0]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD1 </td><td>[keypad1]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD2 </td><td>[keypad2]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD3 </td><td>[keypad3]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD4 </td><td>[keypad4]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD5 </td><td>[keypad5]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD6 </td><td>[keypad6]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD7 </td><td>[keypad7]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD8 </td><td>[keypad8]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD9 </td><td>[keypad9]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD_PERIOD </td><td>[keypad.]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD_COMMA </td><td>[keypad,]</td></tr>
    * <tr><td>MNEMONIC_KEYPAD_MINUS </td><td>[keypad-]</td></tr>
    * <tr><td>MNEMONIC_FIELD_EXIT </td><td>[fldext]</td></tr>
    * <tr><td>MNEMONIC_FIELD_PLUS </td><td>[field+]</td></tr>
    * <tr><td>MNEMONIC_FIELD_MINUS </td><td>[field-]</td></tr>
    * <tr><td>MNEMONIC_BEGIN_OF_FIELD </td><td>[bof]</td></tr>
    * <tr><td>MNEMONIC_PA1 </td><td>[pa1]</td></tr>
    * <tr><td>MNEMONIC_PA2 </td><td>[pa2]</td></tr>
    * <tr><td>MNEMONIC_PA3 </td><td>[pa3]</td></tr>
    * <tr><td>MNEMONIC_SYSREQ </td><td>[sysreq]</td></tr>
    * <tr><td>MNEMONIC_RESET </td><td>[reset]</td></tr>
    * <tr><td>MNEMONIC_ATTN </td><td>[attn]</td></tr>
    * <tr><td>MNEMONIC_MARK_LEFT </td><td>[markleft]</td></tr>
    * <tr><td>MNEMONIC_MARK_RIGHT </td><td>[markright]</td></tr>
    * <tr><td>MNEMONIC_MARK_UP </td><td>[markup]</td></tr>
    * <tr><td>MNEMONIC_MARK_DOWN </td><td>[markdown]</td></tr>
    *
    * </table>
    *
    * @param text The string of characters to be sent
    * @param location Where to send the characters.
    *
    * @see #sendAid
    *
    */
   public void sendKeys(String text) {

      if (keysBuffered) {
         text = bufferedKeys + text;
         keysBuffered = false;
         bufferedKeys = "";
      }

      // check to see if position is in a field and if it is then change
      //   current field to that field
      isInField(lastPos,true);

      strokenizer.setKeyStrokes(text);
      String s;
      boolean done = false;
      while (strokenizer.hasMoreKeyStrokes() && !done) {
         s = strokenizer.nextKeyStroke();
         if (s.length() == 1) {
            simulateKeyStroke(s.charAt(0));
         }
         else {

            if (s != null)
               simulateMnemonic(getMnemonicValue(s));
            else

               System.out.println(" mnemonic " + s);
         }
         if (keyboardLocked) {
            bufferedKeys = strokenizer.getUnprocessedKeyStroked();
            if (bufferedKeys != null)
               keysBuffered = true;
            done = true;
         }
      }
   }

   /**
    * The sendAid method sends an "aid" keystroke to the virtual screen. These
    * aid keys can be thought of as special keystrokes, like the Enter key,
    * PF1-24 keys or the Page Up key. All the valid special key values are
    * contained in the AID_ enumeration:
    *
    * @param aidKey The aid key to be sent to the host
    *
    * @see #sendKeys
    * @see #AID_CLEAR
    * @see #AID_ENTER
    * @see #AID_HELP
    * @see #AID_ROLL_UP
    * @see #AID_ROLL_DOWN
    * @see #AID_ROLL_LEFT
    * @see #AID_ROLL_RIGHT
    * @see #AID_PRINT
    * @see #AID_PF1
    * @see #AID_PF2
    * @see #AID_PF3
    * @see #AID_PF4
    * @see #AID_PF5
    * @see #AID_PF6
    * @see #AID_PF7
    * @see #AID_PF8
    * @see #AID_PF9
    * @see #AID_PF10
    * @see #AID_PF11
    * @see #AID_PF12
    * @see #AID_PF13
    * @see #AID_PF14
    * @see #AID_PF15
    * @see #AID_PF16
    * @see #AID_PF17
    * @see #AID_PF18
    * @see #AID_PF19
    * @see #AID_PF20
    * @see #AID_PF21
    * @see #AID_PF22
    * @see #AID_PF23
    * @see #AID_PF24
    */
   public void sendAid(int aidKey) {

      sessionVT.sendAidKey(aidKey);
   }

   protected boolean simulateMnemonic(int mnem){

      boolean simulated = false;

      switch (mnem) {

         case AID_CLEAR :
         case AID_ENTER :
         case AID_PF1 :
         case AID_PF2 :
         case AID_PF3 :
         case AID_PF4 :
         case AID_PF5 :
         case AID_PF6 :
         case AID_PF7 :
         case AID_PF8 :
         case AID_PF9 :
         case AID_PF10 :
         case AID_PF11 :
         case AID_PF12 :
         case AID_PF13 :
         case AID_PF14 :
         case AID_PF15 :
         case AID_PF16 :
         case AID_PF17 :
         case AID_PF18 :
         case AID_PF19 :
         case AID_PF20 :
         case AID_PF21 :
         case AID_PF22 :
         case AID_PF23 :
         case AID_PF24 :
         case AID_ROLL_DOWN :
         case AID_ROLL_UP :
         case AID_ROLL_LEFT :
         case AID_ROLL_RIGHT :

            sendAid(mnem);
            simulated  = true;

            break;
         case AID_HELP :
            sessionVT.sendHelpRequest();
            simulated  = true;
            break;

         case AID_PRINT :
            sessionVT.hostPrint(1);
            simulated  = true;
            break;

         case BACK_SPACE :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {

               if (screenFields.getCurrentField().startPos() == lastPos)
                  displayError(ERR_CURSOR_PROTECTED);

               else {
                  setCursorOff();
                  screenFields.getCurrentField().getKeyPos(lastPos);
                  screenFields.getCurrentField().changePos(-1);
                  resetDirty(screenFields.getCurrentField().getCurrentPos());
                  shiftLeft(screenFields.getCurrentField().getCurrentPos());
                  updateDirty();
                  setCursorOn();
                  screenFields.setCurrentFieldMDT();

                  simulated  = true;
               }
            }
            else {
               displayError(ERR_CURSOR_PROTECTED);

            }
            break;
         case BACK_TAB :
            gotoFieldPrev();

            if (screenFields.isCurrentFieldContinued()) {
               do {
                  gotoFieldPrev();
               }
               while (screenFields.isCurrentFieldContinuedMiddle() ||
                  screenFields.isCurrentFieldContinuedLast());
            }
            isInField(lastPos);
            simulated  = true;
            break;
         case UP :
         case MARK_UP :
            process_XY(lastPos - numCols);
            simulated = true;
            break;
         case DOWN :
         case MARK_DOWN :
            process_XY(lastPos + numCols);
            simulated = true;
            break;
         case LEFT :
         case MARK_LEFT :
            process_XY(lastPos - 1);
            simulated = true;
            break;
         case RIGHT :
         case MARK_RIGHT :
            process_XY(lastPos + 1);
            simulated = true;
            break;
         case NEXTWORD :
            gotoNextWord();
            simulated = true;
            break;
         case PREVWORD :
            gotoPrevWord();
            simulated = true;
            break;
         case DELETE :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {

                  setCursorOff();
                  resetDirty(lastPos);
                  screenFields.getCurrentField().getKeyPos(lastPos);
                  shiftLeft(screenFields.getCurrentFieldPos());
                  screenFields.setCurrentFieldMDT();
                  updateDirty();
                  setCursorOn();
                  simulated  = true;
            }
            else {
               displayError(ERR_CURSOR_PROTECTED);
            }

            break;
         case TAB :
            if (screenFields.getCurrentField() != null && !screenFields.isCurrentFieldContinued()) {
               gotoFieldNext();
            }
            else {
               do {
                  gotoFieldNext();
               }
               while (screenFields.getCurrentField() != null && (screenFields.isCurrentFieldContinuedMiddle() ||
                     screenFields.isCurrentFieldContinuedLast()));
            }
            isInField(lastPos);
            simulated  = true;

            break;
         case EOF :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {
               int where = endOfField(screenFields.getCurrentField().startPos(),true);
               if (where > 0) {
                  goto_XY((where / numCols) + 1,(where % numCols) + 1);
               }
               simulated = true;
            }
            else {
               displayError(ERR_CURSOR_PROTECTED);
            }
            resetDirty(lastPos);

            break;
         case ERASE_EOF :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {

               setCursorOff();
               int where = lastPos;
               resetDirty(lastPos);
               if (fieldExit()) {
                  screenFields.setCurrentFieldMDT();
                  if (!screenFields.isCurrentFieldContinued()) {
                     gotoFieldNext();
                  }
                  else {
                     do {
                        gotoFieldNext();
                        if (screenFields.isCurrentFieldContinued())
                           fieldExit();
                     }
                     while (screenFields.isCurrentFieldContinuedMiddle() ||
                        screenFields.isCurrentFieldContinuedLast());
                  }
               }
               updateDirty();
               goto_XY(where);
               setCursorOn();
               simulated  = true;

            }
            else {
                  displayError(ERR_CURSOR_PROTECTED);
            }

            break;
         case ERASE_FIELD :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {

               setCursorOff();
               int where = lastPos;
               lastPos = screenFields.getCurrentField().startPos();
               resetDirty(lastPos);
               if (fieldExit()) {
                  screenFields.setCurrentFieldMDT();
                  if (!screenFields.isCurrentFieldContinued()) {
                     gotoFieldNext();
                  }
                  else {
                     do {
                        gotoFieldNext();
                        if (screenFields.isCurrentFieldContinued())
                           fieldExit();
                     }
                     while (screenFields.isCurrentFieldContinuedMiddle() ||
                        screenFields.isCurrentFieldContinuedLast());
                  }
               }
               updateDirty();
               goto_XY(where);
               setCursorOn();
               simulated  = true;

            }
            else {
                  displayError(ERR_CURSOR_PROTECTED);
            }

            break;
         case INSERT :
            setCursorOff();
            // we toggle it
            insertMode = insertMode ? false : true;
            setCursorOn();
            break;
         case HOME :
            // position to the home position set
            if (lastPos + numCols + 1 != homePos) {
               goto_XY(homePos - numCols - 1);
               // now check if we are in a field
               isInField(lastPos);
            }
            else
               gotoField(1);
            break;
         case KEYPAD_0 :
            simulated = simulateKeyStroke('0');
            break;
         case KEYPAD_1 :
            simulated = simulateKeyStroke('1');
            break;
         case KEYPAD_2 :
            simulated = simulateKeyStroke('2');
            break;
         case KEYPAD_3 :
            simulated = simulateKeyStroke('3');
            break;
         case KEYPAD_4 :
            simulated = simulateKeyStroke('4');
            break;
         case KEYPAD_5 :
            simulated = simulateKeyStroke('5');
            break;
         case KEYPAD_6 :
            simulated = simulateKeyStroke('6');
            break;
         case KEYPAD_7 :
            simulated = simulateKeyStroke('7');
            break;
         case KEYPAD_8 :
            simulated = simulateKeyStroke('8');
            break;
         case KEYPAD_9 :
            simulated = simulateKeyStroke('9');
            break;
         case KEYPAD_PERIOD :
            simulated = simulateKeyStroke('.');
            break;
         case KEYPAD_COMMA :
            simulated = simulateKeyStroke(',');
            break;
         case KEYPAD_MINUS :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {

               int s = screenFields.getCurrentField().getFieldShift();
               if (s == 3 || s == 5 || s == 7) {
                  screen[lastPos].setChar('-');
                  resetDirty(lastPos);
                  advancePos();
                  if (fieldExit()) {
                     screenFields.setCurrentFieldMDT();
                     if (!screenFields.isCurrentFieldContinued()) {
                        gotoFieldNext();
                     }
                     else {
                        do {
                           gotoFieldNext();
                        }
                        while (screenFields.isCurrentFieldContinuedMiddle() ||
                              screenFields.isCurrentFieldContinuedLast());
                     }
                     simulated  = true;
                     updateDirty();
                     if (screenFields.isCurrentFieldAutoEnter())
                        sendAid(AID_ENTER);

                  }
               }
               else {
                  displayError(ERR_FIELD_MINUS);

               }
            }
            else {
               displayError(ERR_CURSOR_PROTECTED);
            }

            break;
         case FIELD_EXIT :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {

               setCursorOff();
               resetDirty(lastPos);
               if (fieldExit()) {
                  screenFields.setCurrentFieldMDT();
                  if (!screenFields.isCurrentFieldContinued()) {
                     gotoFieldNext();
                  }
                  else {
                     do {
                        gotoFieldNext();
                        if (screenFields.isCurrentFieldContinued())
                           fieldExit();
                     }
                     while (screenFields.isCurrentFieldContinuedMiddle() ||
                        screenFields.isCurrentFieldContinuedLast());
                  }
               }
               updateDirty();
               setCursorOn();
               simulated  = true;
               if (screenFields.isCurrentFieldAutoEnter())
                  sendAid(AID_ENTER);

            }
            else {
               displayError(ERR_CURSOR_PROTECTED);
            }

            break;
         case FIELD_PLUS :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {

               setCursorOff();
               resetDirty(lastPos);
               if (fieldExit()) {
                  screenFields.setCurrentFieldMDT();
                  if (!screenFields.isCurrentFieldContinued()) {
                     gotoFieldNext();
                  }
                  else {
                     do {
                        gotoFieldNext();
                     }
                     while (screenFields.isCurrentFieldContinuedMiddle() ||
                           screenFields.isCurrentFieldContinuedLast());
                  }
               }
               updateDirty();
               setCursorOn();
               simulated  = true;
               if (screenFields.isCurrentFieldAutoEnter())
                  sendAid(AID_ENTER);

            }
            else {
               displayError(ERR_CURSOR_PROTECTED);
            }

            break;
         case FIELD_MINUS :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {

               int s = screenFields.getCurrentField().getFieldShift();
               if (s == 3 || s == 5 || s == 7) {
                  setCursorOff();
                  screen[lastPos].setChar('-');

                  resetDirty(lastPos);
                  advancePos();
                  if (fieldExit()) {
                     screenFields.setCurrentFieldMDT();
                     if (!screenFields.isCurrentFieldContinued()) {
                        gotoFieldNext();
                     }
                     else {
                        do {
                           gotoFieldNext();
                        }
                        while (screenFields.isCurrentFieldContinuedMiddle() ||
                              screenFields.isCurrentFieldContinuedLast());
                     }
                  }
                  updateDirty();
                  setCursorOn();
                  simulated  = true;
                  if (screenFields.isCurrentFieldAutoEnter())
                     sendAid(AID_ENTER);

               }
               else {
                  displayError(ERR_FIELD_MINUS);

               }
            }
            else {
               displayError(ERR_CURSOR_PROTECTED);
            }

            break;
         case BOF :
            if (screenFields.getCurrentField() != null &&
               screenFields.withinCurrentField(lastPos)
               && !screenFields.isCurrentFieldBypassField()) {
               int where = screenFields.getCurrentField().startPos();
               if (where > 0) {
                  goto_XY(where);
               }
               simulated = true;
            }
            else {
               displayError(ERR_CURSOR_PROTECTED);
            }
            resetDirty(lastPos);

            break;
         case SYSREQ :
            sessionVT.systemRequest();
            simulated = true;
            break;
         case RESET :
            restoreErrorLine();
            setStatus(STATUS_ERROR_CODE,STATUS_VALUE_OFF,"");
            isInField(lastPos);
            simulated = true;
            updateDirty();
            break;
         case COPY :
            copyMe();
            break;
         case PASTE :
            pasteMe(false);
            break;
         case ATTN :
            sessionVT.sendAttentionKey();
            simulated  = true;
            break;
         default :
            System.out.println(" Mnemonic not supported " + mnem);
            break;

      }

      return simulated;
   }

   protected boolean simulateKeyStroke(char c){

      if (isStatusErrorCode() && !Character.isISOControl(c) && !keyProcessed) {
         restoreErrorLine();
         setStatus(STATUS_ERROR_CODE,STATUS_VALUE_OFF,null);
      }

      boolean updateField = false;
      boolean numericError = false;
      boolean updatePos = false;
      boolean autoEnter = false;

      if (!Character.isISOControl(c)) {

         if (screenFields.getCurrentField() != null &&
            screenFields.withinCurrentField(lastPos)
            && !screenFields.isCurrentFieldBypassField()) {


            switch (screenFields.getCurrentFieldShift()) {
               case 0:  // Alpha shift
               case 2:  // Numeric Shift
                  updateField = true;
                  break;
               case 1:  // Alpha Only
                  if(Character.isLetter(c) || c == ',' || c == '-' || c == '.' || c == ' ')
                     updateField = true;
                  break;
               case 3: // Numeric only
                  if(Character.isDigit(c) || c == '+' || c == ',' || c == '-' || c == '.' || c == ' ')
                     updateField = true;
                  else
                     numericError = true;
                  break;
               case 5: // Digits only
                  if(Character.isDigit(c))
                     updateField = true;
                  else
                     displayError(ERR_NUMERIC_09);
                  break;
               case 7: // Signed numeric
                  if(Character.isDigit(c) || c == '+' || c == '-')
                     if (lastPos == screenFields.getCurrentField().endPos()
                              && (c != '+' && c != '-'))
                        displayError(ERR_INVALID_SIGN);
                     else
                        updateField = true;
                  else
                     displayError(ERR_NUMERIC_09);
                  break;
            }

            if (updateField) {
               if (screenFields.isCurrentFieldToUpper())
                  c = Character.toUpperCase(c);

               setCursorOff();
               updatePos = true;
               resetDirty(lastPos);

               if (insertMode) {
                  if (endOfField(false) != screenFields.getCurrentField().endPos())
                     shiftRight(lastPos);
                  else {

                     displayError(ERR_NO_ROOM_INSERT);
                     updatePos = false;
                  }

               }

               if (updatePos) {
                  screenFields.getCurrentField().getKeyPos(getRow(lastPos),getCol(lastPos));
                  screenFields.getCurrentField().changePos(1);

                  screen[lastPos].setChar(c);

                  screenFields.setCurrentFieldMDT();

                  // if we have gone passed the end of the field then goto the next field
                  if (!screenFields.withinCurrentField(screenFields.getCurrentFieldPos())) {
                     if (screenFields.isCurrentFieldAutoEnter()) {
                        autoEnter = true;
                     }
                     else if (!screenFields.isCurrentFieldFER())
                        gotoFieldNext();
                  }
                  else
                     goto_XY(screenFields.getCurrentField().getCursorRow() + 1,screenFields.getCurrentField().getCursorCol() + 1);
               }

               updateImage(dirty);
               setCursorOn();
               if (autoEnter)
                  sendAid(AID_ENTER);
            }
            else {
               if (numericError) {
                  displayError(ERR_NUMERIC_ONLY);
               }
            }
         }
         else {
            displayError(ERR_CURSOR_PROTECTED);
         }

      }
      return updatePos;
   }

   protected void crossHair() {
      setCursorOff();
      crossHair++;
      if (crossHair > 3)
         crossHair = 0;
      setCursorOn();
   }

   /**
    * Method: endOfField <p>
    *
    * convenience method that call endOfField with
    *    lastRow
    *    lastCol
    *    and passes the posSpace to that method
    *
    * @param posSpace value of type boolean - specifying to return the position
    *           of the the last space or not
    * @return a value of type int - the screen postion (row * columns) + col
    *
    */
   private int endOfField(boolean posSpace) {
      return endOfField(lastPos, posSpace);
   }

   /**
    * Method: endOfField <p>
    *
    * gets the position of the last character of the current field
    *    posSpace parameter tells the routine whether to return the position
    *    of the last space (<= ' ') or the last non space
    *    posSpace == true  last occurrence of char <= ' '
    *    posSpace == false last occurrence of char > ' '
    *
    * @param row value of type int - the row to start from
    * @param row value of type int - col the col to start from
    * @param posSpace value of type boolean - specifying to return the position
    *           of the the last space or not
    * @return a value of type int - the screen postion (row * columns) + col
    *
    */
   private int endOfField(int pos, boolean posSpace) {

      int endPos = screenFields.getCurrentField().endPos();
      int fePos = endPos;
      // get the number of characters to the right
      int count = endPos - pos;

      // first lets get the real ending point without spaces and the such
      while (screen[endPos].getChar() <= ' ' && count-- > 0) {

         endPos--;
      }

      if (endPos == fePos) {

         return endPos;

      }
      else {
         screenFields.getCurrentField().getKeyPos(endPos);
         if (posSpace)
            screenFields.getCurrentField().changePos(+1);

         return screenFields.getCurrentFieldPos();

      }
   }


   private boolean fieldExit() {

      int pos = lastPos;
      boolean mdt = false;
      int end = endOfField(false);  // get the ending position of the first
                                    // non blank character in field

      // get the number of characters to the right
      int count = (end - screenFields.getCurrentField().startPos()) -
                  screenFields.getCurrentField().getKeyPos(pos);

      for (;count >= 0; count--) {
         screen[pos].setChar(initChar);
         setDirty(pos);
         pos++;
         mdt = true;
      }

      if (screenFields.getCurrentField().getAdjustment() != 0) {

         switch (screenFields.getCurrentField().getAdjustment()) {

            case 5:
               System.out.println("Right adjust, zero fill " + screenFields.getCurrentField().getAdjustment());
               rightAdjustField('0');

               break;
            case 6:
               System.out.println("Right adjust, blank fill " + screenFields.getCurrentField().getAdjustment());
               rightAdjustField(' ');

               break;
            case 7:
               System.out.println("Mandatory fill " +screenFields.getCurrentField().getAdjustment());
               break;


         }
      }


      return mdt;
   }

   private void rightAdjustField(char fill) {

      int end = endOfField(false);  // get the ending position of the first
                                    // non blank character in field

      // get the number of characters to the right
      int count = screenFields.getCurrentField().endPos() - end;

      // subtract 1 from count for signed numeric - note for later
      if (screenFields.getCurrentField().isSignedNumeric()) {
         if (screen[end-1].getChar() != '-')
            count--;
      }

      int pos = screenFields.getCurrentField().startPos();

      while (count-- >= 0) {

         shiftRight(pos);
         screen[pos].setChar(fill);
         setDirty(pos);

      }

   }

   private void shiftLeft(int sPos) {

      int endPos = 0;

      int pos = sPos;
      int pPos = sPos;

      ScreenField sf = screenFields.getCurrentField();
      int end;
      int count;
      do {
         end = endOfField(pPos,false);  // get the ending position of the first
                                       // non blank character in field

         count = (end - screenFields.getCurrentField().startPos()) -
                     screenFields.getCurrentField().getKeyPos(pPos);

         // now we loop through and shift the remaining characters to the left
         while (count-- > 0) {
              pos++;
            screen[pPos].setChar(screen[pos].getChar());
            setDirty(pPos);
            pPos = pos;

         }

         if (screenFields.isCurrentFieldContinued()) {
            gotoFieldNext();
            if (screenFields.getCurrentField().isContinuedFirst())
               break;

            pos = screenFields.getCurrentField().startPos();
            screen[pPos].setChar(
               screen[pos].getChar()
            );
            setDirty(pPos);

            pPos = pos;

         }
      }
      while (screenFields.isCurrentFieldContinued() && !screenFields.getCurrentField().isContinuedFirst());

      if (end >= 0 && count >= -1) {

         endPos = end;
      }
      else {
         endPos = sPos;

      }

      screenFields.setCurrentField(sf);
      screen[endPos].setChar(initChar);
      setDirty(endPos);
      goto_XY(screenFields.getCurrentFieldPos());
      sf = null;

   }

   private void shiftRight(int sPos) {

      int end = endOfField(true);  // get the ending position of the first
                                    // non blank character in field
      int pos = end;
      int pPos = end;

      int count = end - sPos;

      // now we loop through and shift the remaining characters to the right
      while (count-- > 0) {

         pos--;
         screen[pPos].setChar(screen[pos].getChar());
         setDirty(pPos);

         pPos = pos;
      }
   }

   public int getRow(int pos) {


      int row = pos / numCols;

      if (row < 0) {

         row =  lastPos / numCols;
      }
      if (row > lenScreen - 1)
         row = lenScreen - 1;

      return row;

   }

   public int getCol(int pos) {

      int col = pos % (getCols());
      if (col > 0)
         return col;
      else
         return 0;
   }

   private int getPos(int row, int col) {

      return (row * numCols) + col;
   }

   // Current position is based on offsets of 1,1 not 0,0
   public int getCurrentPos() {

      return lastPos + numCols + 1;

   }

   /**
    *  I got this information from a tcp trace of each error.  I could not find
    *  any documenation for this.  Maybe there is but I could not find it.  If
    *  anybody finds this documention could you please send me a copy.  Please
    *  note that I did not look that hard either.
    *
    *
    * 0000:  00 50 73 1D 89 81 00 50 DA 44 C8 45 08 00 45 00 .Ps....P.D.E..E.
    * 0010:  00 36 E9 1C 40 00 80 06 9B F9 C1 A8 33 58 C0 A8 .6..@.......3X..
    * 0020:  C0 02 06 0E 00 17 00 52 6E 88 73 40 DE CB 50 18 .......Rn.s@..P.
    * 0030:  20 12 3C 53 00 00 00 0C 12 A0 00 00 04 01 00 00  .<S............
    * 0040:  00 05 FF EF                                     ....
    * ----------||
    *    The 00 XX is the code to be sent.  I found the following
    *
    *     ERR_CURSOR_PROTECTED      = 0x05;
    *     ERR_INVALID_SIGN          = 0x11;
    *     ERR_NO_ROOM_INSERT        = 0x12;
    *     ERR_NUMERIC_ONLY          = 0x09;
    *     ERR_NUMERIC_09            = 0x10;
    *     ERR_FIELD_MINUS           = 0x16;
    *
    *    I am tired of typing and they should be self explanitory.  Finding them
    *    in the first place was the pain.
    *
    */

   private void displayError (int ec) {
      saveHomePos = homePos;
      homePos = lastPos + numCols + 1;
      pendingInsert = true;
      gui.sendNegResponse2(ec);

   }

   private void process_XY(int pos) {

      if (pos < 0)
         pos = lenScreen + pos ;
      if (pos > lenScreen - 1)
         pos = pos - lenScreen;
      goto_XY(pos);
   }

   public boolean isUsingGuiInterface() {

      return guiInterface;
   }

   public boolean isInField() {

      return isInField(lastPos,true);
   }

   public boolean isInField(int pos, boolean chgToField) {

      return screenFields.isInField(pos,chgToField);
   }

   public boolean isInField(int pos) {

      return screenFields.isInField(pos,true);
   }

   public boolean isInField(int row, int col) {

      return isInField(row,col,true);
   }

   public boolean isInField(int row, int col, boolean chgToField) {
      return screenFields.isInField((row * numCols) + col,chgToField);
   }


   public int getRows() {

      return numRows;

   }

   public int getCols() {

      return numCols;

   }
   public int getCurrentRow() {

      return (lastPos / numCols) + 1;

   }

   public int getCurrentCol() {

      return (lastPos % numCols) + 1;

   }

   // Get the last position
   protected int getLastPos() {

      return lastPos;

   }

   public StringBuffer getHSMore() {
      return hsMore;
   }

   public StringBuffer getHSBottom() {
      return hsBottom;
   }

   public boolean getColSepLine() {
      return colSepLine;
   }

   public boolean getShowHex() {
      return showHex;
   }

   public char[] getScreenAsChars() {
      char[] sac = new char[lenScreen];
      char c;

      for (int x = 0; x < lenScreen; x++) {
         c = screen[x].getChar();
         // only draw printable characters (in this case >= ' ')
         if (c >= ' ' && !screen[x].nonDisplay) {
            sac[x] = c;
            if (screen[x].underLine && c <= ' ')
               sac[x] = '_';

         }
         else
            sac[x] = ' ';

      }
      return sac;
   }

   public void setKeyboardLocked (boolean k) {
//      System.out.println(" lock it " + k);
      keyboardLocked = k;
      if (!keyboardLocked) {

         if (keysBuffered) {

            sendKeys("");
         }

      }

   }

   public boolean isKeyboardLocked () {
      return keyboardLocked;
   }

   // this routine is based on offset 1,1 not 0,0
   //  it will translate to offset 0,0 and call the goto_XY(int pos)
   //  it is mostly used from external classes that use the 1,1 offset
   public void goto_XY(int row,int col) {
      goto_XY(((row - 1) * numCols) + (col-1));
   }

   // this routine is based on offset 0,0 not 1,1
   public void goto_XY(int pos) {
      updateCursorLoc();
      lastPos = pos;
      updateCursorLoc();
   }

   public void setCursorOn() {
      updateCursorLoc = true;
//      System.out.println("cursor on");
      updateCursorLoc();
   }

   public void setCursorOff() {

      updateCursorLoc();
      updateCursorLoc = false;


   }

   public boolean isMasterMDT() {

      return screenFields.isMasterMDT();

   }

   public boolean gotoField(int f) {

      int sizeFields = screenFields.getSize();

      if (f > sizeFields || f <= 0)
         return false;

      screenFields.setCurrentField(screenFields.getField(f-1));

      while (screenFields.isCurrentFieldBypassField() && f < sizeFields) {

         screenFields.setCurrentField(screenFields.getField(f++));

      }
      return gotoField(screenFields.getCurrentField());
   }

   protected boolean gotoField(ScreenField f) {
      if (f != null) {
         goto_XY(f.startPos());
         return true;
      }
      else {
         return false;
      }

   }

   private void gotoNextWord() {

      int pos = lastPos;
      setCursorOff();

      if (screen[lastPos].getChar() > ' ') {
         advancePos();
         // get the next space character
         while (screen[lastPos].getChar() > ' '
                  && pos != lastPos ) {
            advancePos();
         }
      }
      else
         advancePos();


      // now that we are positioned on the next space character get the
      // next none space character
      while (screen[lastPos].getChar() <= ' '
               && pos != lastPos) {
         advancePos();
      }


      setCursorOn();

   }

   private void gotoPrevWord() {

      int pos = lastPos;
      setCursorOff();

      changePos(-1);

      // position previous white space character
      while (screen[lastPos].getChar() <= ' ') {
         changePos(-1);
         if (pos == lastPos)
            break;
      }

      changePos(-1);
      // get the previous space character
      while (screen[lastPos].getChar() > ' '
               && pos != lastPos) {
         changePos(-1);
      }

      // and position one position more should give us the beginning of word
      advancePos();
      setCursorOn();

   }

   private void gotoFieldNext() {

      screenFields.gotoFieldNext();
   }

   private void gotoFieldPrev() {

      screenFields.gotoFieldPrev();
   }

   public void createWindow(int depth, int width, int type, boolean gui,
                              int monoAttr, int colorAttr,
                              int ul, int upper, int ur, int left, int right,
                              int ll, int bottom, int lr) {


      int c = getCol(lastPos);
      int w = 0;
      width ++;

      w=width;
      // set leading attribute byte
      screen[lastPos].setCharAndAttr(initChar,initAttr,true);
      setDirty(lastPos);

      advancePos();
      // set upper left
      screen[lastPos].setCharAndAttr((char)ul,colorAttr,false);
      if (gui)
         screen[lastPos].setUseGUI(ScreenChar.UPPER_LEFT);
      setDirty(lastPos);

      advancePos();

      // draw top row

      while (w-- >= 0)   {
         screen[lastPos].setCharAndAttr((char)upper,colorAttr,false);
         if (gui)
            screen[lastPos].setUseGUI(ScreenChar.UPPER);
         setDirty(lastPos);
         advancePos();
      }

      // set upper right
      screen[lastPos].setCharAndAttr((char)ur,colorAttr,false);
      if (gui)
         screen[lastPos].setUseGUI(ScreenChar.UPPER_RIGHT);
      setDirty(lastPos);
      advancePos();

      // set ending attribute byte
      screen[lastPos].setCharAndAttr(initChar,initAttr,true);
      setDirty(lastPos);

      lastPos = ((getRow(lastPos) +1) * numCols) + c;
      // now handle body of window
      while (depth-- > 0) {

         // set leading attribute byte
         screen[lastPos].setCharAndAttr(initChar,initAttr,true);
         setDirty(lastPos);
         advancePos();

         // set left
         screen[lastPos].setCharAndAttr((char)left,colorAttr,false);
         if (gui)
            screen[lastPos].setUseGUI(ScreenChar.LEFT);
         setDirty(lastPos);
         advancePos();

         w=width;
         // fill it in
         while (w-- >= 0)   {
            screen[lastPos].setCharAndAttr(initChar,initAttr,true);
            screen[lastPos].setUseGUI(ScreenChar.NO_GUI);
            setDirty(lastPos);
            advancePos();
         }

         // set right
         screen[lastPos].setCharAndAttr((char)right,colorAttr,false);
         if (gui)
            screen[lastPos].setUseGUI(ScreenChar.RIGHT);
         setDirty(lastPos);
         advancePos();

         // set ending attribute byte
         screen[lastPos].setCharAndAttr(initChar,initAttr,true);
         setDirty(lastPos);

         lastPos = ((getRow(lastPos) +1) * numCols) + c;
      }

      // set leading attribute byte
      screen[lastPos].setCharAndAttr(initChar,initAttr,true);
      setDirty(lastPos);
      advancePos();

      // set lower left
      screen[lastPos].setCharAndAttr((char)ll,colorAttr,false);
      if (gui)
         screen[lastPos].setUseGUI(ScreenChar.LOWER_LEFT);
      setDirty(lastPos);
      advancePos();

      w=width;
      // draw bottom row
      while (w-- >= 0)   {
         screen[lastPos].setCharAndAttr((char)bottom,colorAttr,false);
         if (gui)
            screen[lastPos].setUseGUI(ScreenChar.BOTTOM);
         setDirty(lastPos);
         advancePos();
      }

      // set lower right
      screen[lastPos].setCharAndAttr((char)lr,colorAttr,false);
      if (gui)
         screen[lastPos].setUseGUI(ScreenChar.LOWER_RIGHT);
      setDirty(lastPos);
      advancePos();

      // set ending attribute byte
      screen[lastPos].setCharAndAttr(initChar,initAttr,true);
      setDirty(lastPos);

   }

   public void createScrollBar(int flag, int totalRowScrollable,
                                 int totalColScrollable,
                                 int sliderRowPos,
                                 int sliderColPos,
                                 int sbSize) {


//      System.out.println("Scrollbar flag: " + flag +
//                           " scrollable Rows: "  + totalRowScrollable +
//                           " scrollable Cols: "  + totalColScrollable +
//                           " thumb Row: " + sliderRowPos +
//                           " thumb Col: " + sliderColPos +
//                           " size: " + sbSize +
//                           " row: " + getRow(lastPos) +
//                           " col: " + getCol(lastPos));

      int sp = lastPos;
      int size = sbSize - 2;

      int thumbPos = (int)(size * (float)((float)sliderColPos / (float)totalColScrollable));
//      System.out.println(thumbPos);
      screen[sp].setCharAndAttr(' ',32,false);
      screen[sp].setUseGUI(ScreenChar.BUTTON_SB_UP);

      int ctr = 0;
      while (ctr < size) {
         sp += numCols;
         screen[sp].setCharAndAttr(' ',32,false);
         if (ctr == thumbPos)
            screen[sp].setUseGUI(ScreenChar.BUTTON_SB_THUMB);
         else
            screen[sp].setUseGUI(ScreenChar.BUTTON_SB_GUIDE);
         ctr++;
      }
      sp += numCols;
      screen[sp].setCharAndAttr(' ',32,false);
      screen[sp].setUseGUI(ScreenChar.BUTTON_SB_DN);

   }

   /**
    * Write the title of the window
    */
   public void writeWindowTitle(int pos, int depth, int width,
                              byte orientation, int monoAttr,
                              int colorAttr, StringBuffer title) {

      int sp = lastPos;
      int len = title.length();

      // get bit 0 and 1 for interrogation
      switch (orientation & 0xc0) {
         case 0x40: // right
            pos += (4 + width - len);
            break;
         case 0x80: // left
            pos +=2;
            break;
         default:  // center
            // this is to place the position to the first text position of the window
            //    the position passed in is the first attribute position, the next
            //    is the border character and then there is another attribute after
            //    that.
            pos += (3 + ((width / 2) - (len / 2)));
            break;

      }

      //  if bit 2 is on then this is a footer
      if ((orientation & 0x20) == 0x20)
         pos += ((depth + 1) * numCols);

//      System.out.println(pos + "," + width + "," + len+ "," + getRow(pos)
//                              + "," + getCol(pos) + "," + ((orientation >> 6) & 0xf0));

      for (int x = 0; x < len ; x++) {
         screen[pos].setChar(title.charAt(x));
         screen[pos++].setUseGUI(ScreenChar.NO_GUI);

      }
   }


   public void addField(int attr, int len, int ffw1, int ffw2, int fcw1, int fcw2) {

      lastAttr = attr;

      screen[lastPos].setCharAndAttr(initChar,lastAttr,true);
      setDirty(lastPos);

      advancePos();

      ScreenField sf = null;

      // from 14.6.12 for Start of Field Order 5940 function manual
      //  examine the format table for an entry that begins at the current
      //  starting address plus 1.
      if (screenFields.existsAtPos(lastPos)) {
         screenFields.setCurrentFieldFFWs(ffw1,ffw2);
      }
      else {
         sf = screenFields.setField(attr,getRow(lastPos),getCol(lastPos),len,ffw1,ffw2,fcw1,fcw2);
         lastPos = sf.startPos();
         int x = len;

         boolean gui = guiInterface;
         if (sf.isBypassField())
            gui = false;

         while (x-- > 0) {

            if (screen[lastPos].getChar() == 0)
               screen[lastPos].setCharAndAttr(' ',lastAttr,false);
            else
               screen[lastPos].setAttribute(lastAttr);

            if (gui)
               screen[lastPos].setUseGUI(ScreenChar.FIELD_MIDDLE);

            advancePos();

         }

         if (gui)
            if (len > 1) {
               screen[sf.startPos()].setUseGUI(ScreenChar.FIELD_LEFT);
               if (lastPos > 0)
                  screen[lastPos-1].setUseGUI(ScreenChar.FIELD_RIGHT);
               else
                  screen[lastPos].setUseGUI(ScreenChar.FIELD_RIGHT);

            }
            else
               screen[lastPos-1].setUseGUI(ScreenChar.FIELD_ONE);

         setEndingAttr(initAttr);

         lastPos = sf.startPos();
      }

//      if (fcw1 != 0 || fcw2 != 0) {
//
//         System.out.println("lr = " + lastRow + " lc = " + lastCol + " " + sf.toString());
//      }
      sf = null;

   }

//      public void addChoiceField(int attr, int len, int ffw1, int ffw2, int fcw1, int fcw2) {
//
//         lastAttr = attr;
//
//         screen[lastPos].setCharAndAttr(initChar,lastAttr,true);
//         setDirty(lastPos);
//
//         advancePos();
//
//         boolean found = false;
//         ScreenField sf = null;
//
//         // from 14.6.12 for Start of Field Order 5940 function manual
//         //  examine the format table for an entry that begins at the current
//         //  starting address plus 1.
//         for (int x = 0;x < sizeFields; x++) {
//            sf = screenFields[x];
//
//            if (lastPos == sf.startPos()) {
//               screenFields.getCurrentField() = sf;
//               screenFields.getCurrentField().setFFWs(ffw1,ffw2);
//               found = true;
//            }
//
//         }
//
//         if (!found) {
//            sf = setField(attr,getRow(lastPos),getCol(lastPos),len,ffw1,ffw2,fcw1,fcw2);
//
//            lastPos = sf.startPos();
//            int x = len;
//
//            boolean gui = guiInterface;
//            if (sf.isBypassField())
//               gui = false;
//
//            while (x-- > 0) {
//
//               if (screen[lastPos].getChar() == 0)
//                  screen[lastPos].setCharAndAttr(' ',lastAttr,false);
//               else
//                  screen[lastPos].setAttribute(lastAttr);
//
//               if (gui)
//                  screen[lastPos].setUseGUI(ScreenChar.FIELD_MIDDLE);
//
//               advancePos();
//
//            }
//
//            if (gui)
//               if (len > 1) {
//                  screen[sf.startPos()].setUseGUI(ScreenChar.FIELD_LEFT);
//                  if (lastPos > 0)
//                     screen[lastPos-1].setUseGUI(ScreenChar.FIELD_RIGHT);
//                  else
//                     screen[lastPos].setUseGUI(ScreenChar.FIELD_RIGHT);
//
//               }
//               else
//                  screen[lastPos-1].setUseGUI(ScreenChar.FIELD_ONE);
//
//            setEndingAttr(initAttr);
//
//            lastPos = sf.startPos();
//         }
//
//   //      if (fcw1 != 0 || fcw2 != 0) {
//   //
//   //         System.out.println("lr = " + lastRow + " lc = " + lastCol + " " + sf.toString());
//   //      }
//         sf = null;
//
//      }

   public ScreenFields getScreenFields() {
      return screenFields;
   }

   public void drawFields() {

      ScreenField sf;

      int sizeFields = screenFields.getSize();
      for (int x = 0;x < sizeFields; x++) {

         sf = screenFields.getField(x);

         if (!sf.isBypassField()) {
            int pos = sf.startPos();

            int l = sf.length;

            boolean f = true;

            if (l > 1) {
               while (l-- > 0) {


                  if (guiInterface && f) {
                     screen[pos].setUseGUI(ScreenChar.FIELD_LEFT);
                     f =false;
                  }
                  else {

                     screen[pos].setUseGUI(ScreenChar.FIELD_MIDDLE);

                  }

                  if (guiInterface && l == 0) {
                     screen[pos].setUseGUI(ScreenChar.FIELD_RIGHT);
                  }

                  pos++;
               }
            }
            else {
               screen[pos].setUseGUI(ScreenChar.FIELD_ONE);
            }
         }
      }


   }

   public void drawField(ScreenField sf) {

      int pos = sf.startPos();

      int x = sf.length;

      while (x-- > 0) {
         setDirty(pos++);
      }
      updateImage(dirty);

   }

   public boolean checkHotSpots() {

      boolean retHS = false;
      retHS = GUIHotSpots.checkHotSpots(this,screen,numRows,numCols,lenScreen,fmWidth,fmHeight);

      return retHS;
   }


   public void setChar(int cByte) {

      if (cByte > 0 && cByte < ' ') {
         screen[lastPos].setCharAndAttr(' ',33,false);
         setDirty(lastPos);

         advancePos();
      }
      else {
         if (lastPos > 0) {
            if (screen[lastPos - 1].isAttributePlace())
               lastAttr = screen[lastPos -1].getCharAttr();
         }

         screen[lastPos].setCharAndAttr((char)cByte,lastAttr,false);
         setDirty(lastPos);
         if (guiInterface && !isInField(lastPos,false))
            screen[lastPos].setUseGUI(ScreenChar.NO_GUI);

         advancePos();
      }

   }


   public void setEndingAttr(int cByte) {

      int attr = lastAttr;
//      System.out.println("setting ending to " + cByte + " lastAttr is " + lastAttr +
//                     " at " + (lastRow + 1) + "," + (lastCol + 1));
//      System.out.print("setting ending to ");

      setAttr(cByte);
      lastAttr = attr;
   }

   public void setAttr(int cByte) {
      lastAttr = cByte;

//      int sattr = screen[getPos(lastRow,lastCol)].getCharAttr();
//         System.out.println("changing from " + sattr + " to attr " + lastAttr +
//                     " at " + (lastRow + 1) + "," + (lastCol + 1));
      screen[lastPos].setCharAndAttr(initChar,lastAttr,true);
      setDirty(lastPos);

      advancePos();
      int pos = lastPos;

      int times = 0;
      while (screen[lastPos].getCharAttr() != lastAttr &&
            !screen[lastPos].isAttributePlace()) {

         screen[lastPos].setAttribute(lastAttr);
         if (guiInterface && !isInField(lastPos,false)) {
            int  g = screen[lastPos].whichGui;
            if (g >= ScreenChar.FIELD_LEFT && g <= ScreenChar.FIELD_ONE)
               screen[lastPos].setUseGUI(ScreenChar.NO_GUI);
         }
         setDirty(lastPos);

         times++;
         advancePos();
      }

      // sanity check for right now
//      if (times > 200)
//         System.out.println("   setAttr = " + times + " start = " + (sr + 1) + "," + (sc + 1));

      lastPos = pos;
   }

   public void updateDirty() {

      // update the image
      updateImage(dirty);
      // update dirty to show that we have already painted that region of the
      //   screen so do not do it again.
      dirty.setBounds(dirty.x,dirty.height,dirty.width,(int)(tArea.getHeight() - dirty.height));

   }

   public void setDirty(int pos) {

      int bx = screen[pos].x;
      int by = screen[pos].y;
      workR.setBounds(bx,by,fmWidth,fmHeight);
      dirty = dirty.union(workR);

   }

   private void setDirty(int row, int col) {

      setDirty(getPos(row,col));

   }

   private void resetDirty(int pos) {

      dirty.setBounds(screen[pos].x,screen[pos].y,fmWidth,fmHeight);
   }

   protected void advancePos() {
      changePos(1);
   }

   protected void changePos(int i) {

      lastPos += i;
      if (lastPos < 0)
         lastPos = lenScreen + lastPos ;
      if (lastPos > lenScreen - 1)
         lastPos = lastPos - lenScreen;

//      System.out.println(lastRow + "," + ((lastPos) / numCols) + "," +
//                         lastCol + "," + ((lastPos) % numCols) + "," +
//                         ((lastRow * numCols) + lastCol) + "," +
//                         (lastPos));

   }

   public void goHome() {

      //  now we try to move to first input field according to
      //  14.6 WRITE TO DISPLAY Command
      //    � If the WTD command is valid, after the command is processed,
      //          the cursor moves to one of three locations:
      //    - The location set by an insert cursor order (unless control
      //          character byte 1, bit 1 is equal to B'1'.)
      //    - The start of the first non-bypass input field defined in the
      //          format table
      //    - A default starting address of row 1 column 1.

      if (pendingInsert) {
         goto_XY(getRow(homePos),getCol(homePos));
         isInField();   // we now check if we are in a field
      }
      else {
         if(!gotoField(1)) {
            homePos = getPos(1,1);
            goto_XY(1,1);
            isInField(0,0);   // we now check if we are in a field
         }
         else {
            homePos = getPos(getCurrentRow(),getCurrentCol());
         }
      }
   }

   public void setPendingInsert(boolean flag, int icX, int icY) {
      pendingInsert = flag;
      if (pendingInsert) {
         homePos = getPos(icX,icY);
      }
   }

   public void setErrorLine (int line) {

      if (line == 0 || line > numRows)
         errorLineNum = numRows;
      else
         errorLineNum = line;
   }

   public int getErrorLine () {
      return errorLineNum;
   }

   public void saveErrorLine() {
      // if there is already an error line saved then do not save it again
      //  This signifies that there was a previous error and the original error
      //  line was not restored yet.
      if (errorLine == null) {
         errorLine = new ScreenChar[numCols];
         int r = getPos(errorLineNum-1,0);

         for (int x = 0;x < numCols; x++) {
            errorLine[x] = new ScreenChar(this);
            errorLine[x].setCharAndAttr(
                              screen[r+x].getChar(),
                              screen[r+x].getCharAttr(),
                              false);
         }
      }
   }

   public void restoreErrorLine() {

      if (errorLine != null) {
         int r = getPos(errorLineNum-1,0);

         for (int x = 0;x < numCols - 1; x++) {
            screen[r + x].setCharAndAttr(errorLine[x].getChar(),errorLine[x].getCharAttr(),false);
         }
         errorLine = null;
         updateImage(0,screen[r].y,bi.getWidth(),fmHeight);
      }
   }

   public void setMessageLightOn() {

      Graphics2D g2d = getWritingArea();
      float Y = (fmHeight * (numRows + 2))- (lm.getLeading() + lm.getDescent());
      g2d.setColor(colorBlue);
      g2d.drawString("MW",(float)mArea.getX(),Y);
      messageLight = true;
      updateImage(mArea.getBounds());
      g2d.dispose();

   }

   public void setMessageLightOff() {

      Graphics2D g2d = getWritingArea();

      g2d.setColor(colorBg);
      g2d.fill(mArea);
      messageLight = false;
      updateImage(mArea.getBounds());
      g2d.dispose();

   }

   public boolean isMessageWait() {

      return messageLight;
   }

   public void setStatus(byte attr,byte value,String s) {

      Graphics2D g2d = getWritingArea();
      statusString = s;

      if (g2d == null)
         return;
      try {
         g2d.setColor(colorBg);
         g2d.fill(sArea);

         float Y = ((int)sArea.getY() + fmHeight)- (lm.getLeading() + lm.getDescent());
         switch (attr) {

            case STATUS_SYSTEM:
               if (value == STATUS_VALUE_ON) {
                  statusXSystem =true;
                  g2d.setColor(colorWhite);

                  if (s != null)
                     g2d.drawString(s,(float)sArea.getX(),Y);
                  else
                     g2d.drawString(xSystem,(float)sArea.getX(),Y);
               }
               else
                  statusXSystem = false;
               break;
            case STATUS_ERROR_CODE:
               if (value == STATUS_VALUE_ON) {
                  g2d.setColor(colorRed);

                  if (s != null)
                     g2d.drawString(s,(float)sArea.getX(),Y);
                  else
                     g2d.drawString(xError,(float)sArea.getX(),Y);

                  statusErrorCode = true;
                  setKeyboardLocked(true);
                  Toolkit.getDefaultToolkit().beep();
               }
               else {
                  statusErrorCode = false;
                  setKeyboardLocked(false);
                  homePos = saveHomePos;
                  saveHomePos = 0;
                  pendingInsert = false;
               }
               break;

         }
         updateImage(sArea.getBounds());
         g2d.dispose();
      }
      catch (Exception e) {

         System.out.println(" setStatus " + e.getMessage());

      }
   }

   public boolean isWithinScreenArea(int x, int y){

      return tArea.contains(x,y);

   }

   public boolean isStatusErrorCode(){

      return statusErrorCode;

   }

   public boolean isXSystem(){

      return statusXSystem;

   }

   /**
    * This routine clears the screen, resets row and column to 0,
    * resets the last attribute to 32, clears the fields, turns insert mode
    * off.
    */
   public void clearAll() {

      lastAttr = 32;
      lastPos = 0;

      clearTable();
      clearScreen();
      screen[0].setAttribute(initAttr);
      insertMode = false;
      cursor.setRect(0,0,0,0);

   }

   /**
    * Clear the fields table
    */
   public void clearTable() {

      setKeyboardLocked(true);
      screenFields.clearFFT();
      pendingInsert = false;
      homePos = -1;
   }

   /**
    * Clear the screen by setting the initial character and initial
    * attribute to all the positions on the screen
    */

   public void clearScreen() {

      for (int x = 0; x < lenScreen; x++) {
         screen[x].setCharAndAttr(' ',initAttr,false);
         screen[x].setUseGUI(ScreenChar.NO_GUI);
      }
//      dirty.setBounds(tArea.getBounds());
      dirty.setBounds(fmWidth * numCols,fmHeight * numRows,0,0);
   }

   public void restoreScreen() {

      lastAttr = 32;
      dirty.setBounds(tArea.getBounds());
      updateImage(dirty);
   }

   /**
    * Returns a pointer to the graphics area that we can draw on
    */
   public Graphics2D getDrawingArea(){

      return bi.getDrawingArea();
   }

  /**
    * Returns a pointer to the graphics area that we can write on
    */
   public Graphics2D getWritingArea(){

      return bi.getWritingArea(font);
   }


   protected synchronized void updateImage(int x, int y , int width, int height) {
      if (gg2d == null) {
         gg2d = (Graphics2D)gui.getGraphics();
//         System.out.println("was null");
      }
      if (bi == null || gg2d == null)
         return;

      g2d.setClip(x,y,width,height);
      if (!cursorActive && x + width <= bi.getWidth(null) &&
          y + height <= (bi.getHeight(null) - fmWidth)) {
         paintComponent2(g2d);
      }

      // fix for jdk1.4 - found this while testing under jdk1.4
      //   if the height and or the width are equal to zero we skip the
      //   the updating of the image.
      if (gui.isVisible() && height > 0 && width > 0) {
         bi.drawImageBuffer(gg2d,x,y,width,height);
//         gg2d.drawImage(bi.getImageBuffer().getSubimage(x,y,width,height),null,x,y);
      }

   }

   protected void updateImage(Rectangle r) {
      updateImage(r.x,r.y,r.width,r.height);

   }

   protected void paintComponent3(Graphics g) {
//      System.out.println("paint from screen");
      Graphics2D g2 = (Graphics2D)g;

//      Rectangle r = g.getClipBounds();

      g2.setColor(colorBg);
      g2.fillRect(0,0,gui.getWidth(),gui.getHeight());

      bi.drawImageBuffer(g2);
//      g2.drawImage(bi.getImageBuffer(),null,0,0);


   }

   protected synchronized void paintComponent2(Graphics2D g2) {

      Rectangle r = g2.getClipBounds();

      g2.setColor(colorBg);
//      System.out.println("PaintComponent " + r);

      g2.fillRect(r.x,r.y,r.width,r.height);

      int sPos = getRowColFromPoint(r.x,
                                 r.y);
      // fix me here
      int er = (numRows - ((((fmHeight * (numRows + 1)) - ((r.y + r.height) + fmHeight)) / fmHeight)));
      int ec = (numCols - ((((fmWidth * (numCols + 1)) - ((r.x + r.width) + fmWidth)) / fmWidth)));

      int sr = getRow(sPos);
      int c = getCol(sPos);
      er--;
      ec--;

//      System.out.println(sr + "," + c + "," + er + "," + ec);
      workR.setBounds(sr,c,ec,er);

      int rows = er - sr;
      int cols = 0;
      int lr = workR.x;
      int lc = 0;


      lr = sPos;

      while (rows-- >= 0) {
         cols = ec - c;
         lc = lr;
         while (cols-- >= 0) {
            screen[lc++].drawChar(g2);

         }
         lr += numCols;
      }

   }

   private void updateCursorLoc() {

      if (updateCursorLoc) {
         cursorActive = cursorActive ? false:true;

         int row = getRow(lastPos);
         int col = getCol(lastPos);

         bi.drawCursor(this,row,col,
                           fmWidth,fmHeight,
                           insertMode, crossHair,
                           cursorSize,colorCursor,
                           colorBg,colorWhite,
                           font);

         cursorActive = false;
      }
   }

   /**
     * Method: checkOffScreenImage <p>
     *
     * This routine will make sure we have something to draw on
     *
     */
   private void checkOffScreenImage() {

      // do we have something already?
      if (bi == null) {

         bi = new GuiGraphicBuffer();

         // allocate a buffer Image with appropriate size
         bi.getImageBuffer(fmWidth * numCols,fmHeight * (numRows + 2));

         // fill in the areas
         tArea = new Rectangle2D.Float(0,0,0,0);
         cArea = new Rectangle2D.Float(0,0,0,0);
         aArea = new Rectangle2D.Float(0,0,0,0);
         sArea = new Rectangle2D.Float(0,0,0,0);
         pArea = new Rectangle2D.Float(0,0,0,0);
         mArea = new Rectangle2D.Float(0,0,0,0);

         // Draw Operator Information Area
         drawOIA();
      }

   }


   private final void resizeScreenArea(int width, int height) {

      Font k = null;
      LineMetrics l;
      FontRenderContext f = null;
      k = GUIGraphicsUtils.getDerivedFont(font,width,height,numRows,numCols,sfh,sfw,ps132);
      f = new FontRenderContext(k.getTransform(),true,true);

      l = k.getLineMetrics("Wy",f);

      if (font.getSize() != k.getSize() || updateFont) {

         // set up all the variables that are used in calculating the new
         // size
         font = k;
         FontRenderContext frc = new FontRenderContext(font.getTransform(),true,true);
         lm = font.getLineMetrics("Wy",frc);
         fmWidth = (int)font.getStringBounds("W",frc).getWidth() + 2;
         fmHeight = (int)(font.getStringBounds("g",frc).getHeight() +
                     lm.getDescent() + lm.getLeading());

         // clear the bufferimage that we use to draw on
//         bi = null;

         bi.resize(fmWidth * numCols,fmHeight * (numRows + 2));
         drawOIA();
         // create the new drawable image that we will use
//         checkOffScreenImage();

         // and loop through the screen buffer to draw the new image with
         // the correct attributes
         for (int m = 0;m < lenScreen; m++) {
            screen[m].setRowCol(getRow(m),getCol(m));

         }
         updateFont = false;
      }
   }

   private void drawOIA() {


      // get ourselves a global pointer to the graphics
      g2d = bi.drawOIA(fmWidth,fmHeight,numRows,numCols,font,colorBg,colorBlue);

      tArea.setRect(bi.getTextArea());
      cArea.setRect(bi.getCommandLineArea());
      aArea.setRect(bi.getScreenArea());
      sArea.setRect(bi.getStatusArea());
      pArea.setRect(bi.getPositionArea());
      mArea.setRect(bi.getMessageArea());

   }

   /**
     * Method: setBounds <p>
     *
     * This routine will calculate the new image size that will be displayed
     * when the frame that holds it is resized.
     *
     * The font characteristics are changed to fit the new size as will as
     * the screen buffer row column offsets so that the characters that
     * make of the screen are displayed correctly
     *
     */
   public final void setBounds(int width, int height) {

      resizeScreenArea(width,height);
      dirty.setBounds(tArea.getBounds());
      if (gui.getGraphics() != null) {
         // do not forget to null out gg2d before update or else there will
         //    be a very hard to trace screen resize problem
         gg2d = null;
         updateDirty();
         setCursorOn();
      }

      // restore statuses that were on the screen before resize
      if (isStatusErrorCode())
         setStatus(STATUS_ERROR_CODE,STATUS_VALUE_ON,statusString);
      if (isXSystem())
         setStatus(STATUS_SYSTEM,STATUS_VALUE_ON,statusString);
      if (isMessageWait())
         setMessageLightOn();
   }

   /**
     * Method: setBounds <p>
     *
     * This routine will calculate the new image size that will be displayed
     * when the frame that holds it is resized.
     *
     * The font characteristics are changed to fit the new size as will as
     * the screen buffer row column offsets so that the characters that
     * make of the screen are displayed correctly
     *
     */
   public final void setBounds(Rectangle r) {

      setBounds(r.width, r.height);
   }

   /**
     * Method: getPreferredSize <p>
     *
     * This routine returns the preferred size of the component that wants
     * to be displayed
     *
     * @return the value of the preferredSize property
     *
     */
   public final Dimension getPreferredSize() {

      return new Dimension(fmWidth * numCols,fmHeight * (numRows + 2));

   }

   /**
     * Method: printMe <p>
     *
     * This routine is responsible for setting up a PrinterJob on this
     * component and initiating the print session.
     *
     */
   public final void printMe() {

      Thread printerThread = new PrinterThread(screen,font,numCols,numRows,colorBg);
      printerThread.start();
//      //--- Create a printerJob object
//      PrinterJob printJob = PrinterJob.getPrinterJob ();
//      printJob.setJobName("tn5250j");
//
//      //--- Set the printable class to this one since we
//      //--- are implementing the Printable interface
//      printJob.setPrintable (this);
//
//
//      //--- Show a print dialog to the user. If the user
//      //--- clicks the print button, then print, otherwise
//      //--- cancel the print job
//      if (printJob.printDialog()) {
//         try {
//            printJob.print();
//         } catch (Exception PrintException) {
//            PrintException.printStackTrace();
//         }
//      }

   }

//   /**
//     * Method: print <p>
//     *
//     * This routine is responsible for rendering a page using
//     * the provided parameters. The result will be a screen
//     * print of the current screen to the printer graphics object
//     *
//     * @param g a value of type Graphics
//     * @param pageFormat a value of type PageFormat
//     * @param page a value of type int
//     * @return a value of type int
//     */
//   public int print (Graphics g, PageFormat pageFormat, int page) {
//
//      Graphics2D g2;
//
//      //--- Validate the page number, we only print the first page
//      if (page == 0) {
//
//         //--- Create a graphic2D object and set the default parameters
//         g2 = (Graphics2D) g;
//         g2.setColor (colorBg);
//
//         //--- Translate the origin to be (0,0)
//         g2.translate (pageFormat.getImageableX (), pageFormat.getImageableY ());
//
//         int w = (int)pageFormat.getImageableWidth() / numCols;     // proposed width
//         int h = (int)pageFormat.getImageableHeight() / numRows;     // proposed height
//
//         Font k = new Font("Courier New",Font.PLAIN,8);
//
//         LineMetrics l;
//         FontRenderContext f = null;
//
//         float j = 1;
//
//         for (; j < 36; j++) {
//
//            // derive the font and obtain the relevent information to compute
//            // the width and height
//            k = font.deriveFont(j);
//            f = new FontRenderContext(k.getTransform(),true,true);
//            l = k.getLineMetrics("Wy",f);
//
//            if (
//                  (w < (int)k.getStringBounds("W",f).getWidth() + 1) ||
//                     h < (int)(k.getStringBounds("g",f).getHeight() +
//                           l.getDescent() + l.getLeading())
//
//               )
//               break;
//         }
//
//         // since we were looking for an overrun of the width or height we need
//         // to adjust the font one down to get the last one that fit.
//         k = font.deriveFont(--j);
//         f = new FontRenderContext(k.getTransform(),true,true);
//         l = k.getLineMetrics("Wy",f);
//
//         // set the font of the print job
//         g2.setFont(k);
//
//         // get the width and height of the character bounds
//         int w1 = (int)k.getStringBounds("W",f).getWidth() + 1;
//         int h1 = (int)(k.getStringBounds("g",f).getHeight() +
//                     l.getDescent() + l.getLeading());
//         int x;
//         int y;
//
//         // loop through all the screen characters and print them out.
//         for (int m = 0;m < numRows; m++)
//            for (int i = 0; i < numCols; i++) {
//               x = w1 * i;
//               y = h1 * m;
//
//               // only draw printable characters (in this case >= ' ')
//               if (screen[getPos(m,i)].getChar() >= ' ' && !screen[getPos(m,i)].nonDisplay) {
//
//                  g2.drawChars(screen[getPos(m,i)].sChar, 0, 1,x , (int)(y + h1 - (l.getDescent() + l.getLeading())-2));
//
//               }
//               // if it is underlined then underline the character
//               if (screen[getPos(m,i)].underLine && !screen[getPos(m,i)].attributePlace)
//                  g.drawLine(x, (int)(y + (h1 - l.getLeading()-3)), (int)(x + w1), (int)(y + (h1 - l.getLeading())-3));
//
//            }
//
//         return (PAGE_EXISTS);
//      }
//      else
//         return (NO_SUCH_PAGE);
//   }

}