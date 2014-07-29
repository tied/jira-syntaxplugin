package jira.plugin.syntaxhighlighter.macro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import syntaxhighlighter.SyntaxHighlighterParserUtil;
import syntaxhighlighter.beans.CodeContainer;
import syntaxhighlighter.brush.Brush;
import syntaxhighlighter.brush.BrushBash;
import syntaxhighlighter.brush.BrushCSharp;
import syntaxhighlighter.brush.BrushCpp;
import syntaxhighlighter.brush.BrushCss;
import syntaxhighlighter.brush.BrushDelphi;
import syntaxhighlighter.brush.BrushDiff;
import syntaxhighlighter.brush.BrushErlang;
import syntaxhighlighter.brush.BrushJScript;
import syntaxhighlighter.brush.BrushJava;
import syntaxhighlighter.brush.BrushJavaFX;
import syntaxhighlighter.brush.BrushPerl;
import syntaxhighlighter.brush.BrushPhp;
import syntaxhighlighter.brush.BrushPlain;
import syntaxhighlighter.brush.BrushPython;
import syntaxhighlighter.brush.BrushRuby;
import syntaxhighlighter.brush.BrushScala;
import syntaxhighlighter.brush.BrushSql;
import syntaxhighlighter.brush.BrushVb;
import syntaxhighlighter.brush.BrushXml;

import com.atlassian.jira.ComponentManager;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.velocity.VelocityManager;

/**
 * Copyright (c) 2012, 2013, 2014 by Holger Schimanski
 * 
 * Macro plugin class for syntax highlighting of source code in description, comments etc. of JIRA issues. 
 * See {@link https://marketplace.atlassian.com/plugins/jira.plugin.syntaxhighlighter.macro.syntaxplugin} for more details. 
 * 
**/
public class SyntaxHighlighterMacro extends BaseMacro {

	private static final String HIGHLIGHT = "highlight";
	private static final String TITLE = "title";
	private static final String FIRST_LINE = "first-line"; //Deprecated
	private static final String FIRSTLINE = "firstline"; //TODO use instead of first-line, default is 1
	private static final String HIDE_LINENUM = "hide-linenum"; //TODO no longer used
	private static final String SHOW_LINENUMS = "linenumbers"; //default is false
	private static final String COLLAPSE = "collapse"; //default is false
	/**
	 * Character ({@value}) used to separate ranges of line numbers.
	 */
	private static final String RANGE_SEPARATOR = "-";

	public boolean hasBody() {
		return true;
	}

	public RenderMode getBodyRenderMode() {
		return RenderMode.allow(RenderMode.F_HTMLESCAPE);
	}

	public boolean isInline() {
		return false;
	}

	@SuppressWarnings("rawtypes")
	public String execute(Map parameters, String body, RenderContext renderContext) throws MacroException {

		//Syntax highlighting with brush
		Brush tmpBrush = getBrush(parameters);
	    CodeContainer tmpCodeContainer = SyntaxHighlighterParserUtil.brush(body, tmpBrush);
	    
	    //First line and hide line num parameter
	    tmpCodeContainer.setShowLineNums(getShowLineNums(parameters));
	    tmpCodeContainer.setFirstLine(getFirstLine(parameters));

	    //Highlighting of code rows
	    List<Integer> highlighted = getHighlight(parameters);
	    for (Integer tmpLineNumHighlighted : highlighted) {
	    	int tmpCodeRowNumHighlighted = tmpLineNumHighlighted.intValue() - tmpCodeContainer.getFirstLine();
	    	if ( tmpCodeRowNumHighlighted < tmpCodeContainer.getCodeRows().size() ){
	    		tmpCodeContainer.getCodeRows().get(tmpCodeRowNumHighlighted).setHighlighted(true);
	    	}
	    }
	    
	    //Put code container as param for velocity
	    Map<String,Object> contextParameters = new HashMap<String,Object>();
	    contextParameters.put("codeContainer", tmpCodeContainer);
	    contextParameters.put("codeTitle", parameters.get(TITLE));

	    //Get HTML rendering using velocity templates
	    VelocityManager tmplManager = ComponentManager.getInstance().getVelocityManager();
		StringBuffer codeBody = new StringBuffer();
		codeBody.append(tmplManager.getBody("templates/", "code.vm", contextParameters));
		
		return codeBody.toString();
		
	}
	
	@SuppressWarnings("rawtypes")
	public List<Integer> getHighlight(Map parameters) {
		List<Integer> ret = new ArrayList<Integer>();
		if ( parameters.containsKey(HIGHLIGHT)){
			String paramValue = parameters.get(HIGHLIGHT).toString();
			ret.addAll(expandRanges(paramValue));
		}
		return ret;
	}	

	@SuppressWarnings("rawtypes")
	public int getFirstLine(Map parameters) {
		try{
			if ( parameters.containsKey(FIRST_LINE)){
				int firstLine = Integer.parseInt(parameters.get(FIRST_LINE).toString());
				return firstLine;
			}
		}
		catch(NumberFormatException e){
			//TODO Log debug
		}
		
		return 1;
	}	

	
	@SuppressWarnings("rawtypes")
	public boolean getCollapse(Map parameters) {
		if ( parameters.containsValue(COLLAPSE) || 
				( parameters.containsKey(COLLAPSE) && parameters.get(COLLAPSE).equals("true") ) ||
				( parameters.containsKey(COLLAPSE) && parameters.get(COLLAPSE).equals("yes") ) ){
			return true;
		} else {
			return false;
		}
	}	
	
	
	@SuppressWarnings("rawtypes")
	public boolean getShowLineNums(Map parameters) {
		if ( parameters.containsValue(SHOW_LINENUMS) || 
				( parameters.containsKey(SHOW_LINENUMS) && parameters.get(SHOW_LINENUMS).equals("true") ) ||
				( parameters.containsKey(SHOW_LINENUMS) && parameters.get(SHOW_LINENUMS).equals("yes") ) ){
			return true;
		} else {
			return false;
		}
	}	
	
	/**
	 * Scans a given string of line numbers for occurrences of a range (eg- 1-3).
	 * Any ranges found will be expanded to sequences.
	 * 
	 * @param ranges
	 *            Comma-separated list of line numbers and ranges in any
	 *            combination. String is expected to be surrounded by [ and ].
	 * @return ranges with all ranges expanded to sequences. Any other token
	 *         will remain unchanged.
	 */
	public List<Integer> expandRanges(String ranges) {
		String[] parts;
		List<Integer> ret = new ArrayList<Integer>();
		
		if (ranges.isEmpty()) return ret;
		
		if (ranges.startsWith("[") && ranges.endsWith("]")) {
			ranges = ranges.substring(1, ranges.length()-1);
		} 
		parts = ranges.split(",");
		for (String part : parts) {
			if (part.contains(RANGE_SEPARATOR)) {
				ret.addAll(rangeToSequence(part));
			} else {
				ret.add(new Integer(part));
			}
		}
		return ret;
		
		//TODO Log.debug Number format exception
	}
	
	/**
	 * Makes a sequence of numbers out of a given range. For Example, "1-3" will
	 * produce "1,2,3". A valid range consists of two numbers separated by the
	 * {@link #RANGE_SEPARATOR}. The second number has to be greater than the first.
	 * 
	 * @param range String representation of the range to expand to sequence
	 * @return A list of Integers or an empty list if any error occurs.
	 */
	public List<Integer> rangeToSequence(String range) {
		String[] parts;
		List<Integer> ret = new ArrayList<Integer>();
		int sequenceStart, sequenceEnd;
		
		parts = range.split(String.valueOf(RANGE_SEPARATOR));
		
		try {
			if (parts.length == 2) {
				sequenceStart = Integer.parseInt(parts[0]);
				sequenceEnd = Integer.parseInt(parts[1]);

				for (int i = sequenceStart; i <= sequenceEnd; i++) {
					ret.add(new Integer(i));
				}
			} else {
				//TODO log.debug wrong string
			}
		} catch (NumberFormatException nfe) {
			// TODO Log debug numberformat exception
		}

		return ret;
	}
	
	
	/**
	 * Returns the brush to be used for the language specified as parameter in {code:...}. Returns plain as default if 
	 * language is not specified or unknown.
	 * 
	 * @param parameters Map of parameters 
	 * @return brush name
	 */
	@SuppressWarnings("rawtypes")
	public Brush getBrush(Map parameters) {
		
		if (parameters.containsKey("0")) {
			String tmpParam = (String) parameters.get("0");
			if ( "erlang".equals(tmpParam) ) {
				return new BrushErlang();
			}
			else if ( "diff".equals(tmpParam) ) {
				return new BrushDiff();
			}
			else if ( "sql".equals(tmpParam) ) {
				return new BrushSql();
			}
			else if ( "css".equals(tmpParam) ) {
				return new BrushCss();
			}
			else if ( "php".equals(tmpParam) ) {
				return new BrushPhp();
			}
			else if ( "ruby".equals(tmpParam) ) {
				return new BrushRuby();
			}
			else if ( "perl".equals(tmpParam) ) {
				return new BrushPerl();
			}
			else if ( "javafx".equals(tmpParam) ) {
				return new BrushJavaFX();
			}
			else if ( "java".equals(tmpParam) ) {
				return new BrushJava();
			}
			else if ( "scala".equals(tmpParam) ) {
				return new BrushScala();
			}
			else if ( "bash".equals(tmpParam) ) {
				return new BrushBash();
			}
			//TODO Gherkin
//			else if ( "gherkin".equals(tmpParam) ) {
//				return new BrushGherkin();
//			}
			//TODO TCL
//			else if ( "tcl".equals(tmpParam) ) {
//				return new BrushTcl();
//			}
			else if (
					"csharp".equals(tmpParam) || 
					"cs".equals(tmpParam) || 
					"c#".equals(tmpParam)  
					) {
				return new BrushCSharp();
			}
			else if (
					"c".equals(tmpParam) || 
					"c++".equals(tmpParam) || 
					"cpp".equals(tmpParam)  
					) {
				return new BrushCpp();
			}
			else if (
					"delphi".equals(tmpParam) || 
					"pas".equals(tmpParam) || 
					"pascal".equals(tmpParam)  
					) {
				return new BrushDelphi();
			}
			//TODO DI
//			else if (
//					"d".equals(tmpParam) || 
//					"di".equals(tmpParam)
//					) {
//				tmpMode = "d";
//			}
			//TODO Objective-C
//			else if (
//					"objc".equals(tmpParam) || 
//					"obj-c".equals(tmpParam)  
//					) {
//				tmpMode = "objc";
//			}
			else if (
					"js".equals(tmpParam) || 
					"javascript".equals(tmpParam) || 
					"jscript".equals(tmpParam)  
					) {
				return new BrushJScript();
			}
			else if (
					"py".equals(tmpParam) || 
					"python".equals(tmpParam) 
					) {
				return new BrushPython();
			}
			else if (
					"vb".equals(tmpParam) || 
					"vbnet".equals(tmpParam) 
					) {
				return new BrushVb();
			}
			else if (
					"xml".equals(tmpParam) || 
					"xhtml".equals(tmpParam) || 
					"xslt".equals(tmpParam) || 
					"html".equals(tmpParam)  
					) {
				return new BrushXml();
			}
		}
		
		return new BrushPlain();
	}


}
