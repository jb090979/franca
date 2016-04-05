/*******************************************************************************
 * Copyright (c) 2016 itemis AG (http://www.itemis.de).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.franca.connectors.omgidl.ui.handlers;

import java.util.Map;

import org.csu.idl.idlmm.TranslationUnit;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.xtext.xbase.lib.ListExtensions;
import org.franca.connectors.omgidl.OMGIDLConnector;
import org.franca.connectors.omgidl.OMGIDLModelContainer;
import org.franca.core.dsl.FrancaPersistenceManager;
import org.franca.core.dsl.ui.util.SpecificConsole;
import org.franca.core.framework.IssueReporter;
import org.franca.core.franca.FModel;

import com.google.inject.Inject;

public class CreateFrancaFromOMGIDLHandler extends AbstractHandler {
	
	@Inject FrancaPersistenceManager saver;
    	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		
        if (selection != null && selection instanceof IStructuredSelection) {
    		SpecificConsole myConsole = new SpecificConsole("Franca");
            final MessageConsoleStream out = myConsole.getOut();
            final MessageConsoleStream err = myConsole.getErr();
            
            if (selection.isEmpty()) {
            	err.println("Please select exactly one file with extension 'xml'!");
                return null;
            }

            IFile file = (IFile) ((IStructuredSelection) selection).getFirstElement();
//            if (! isOMGIDLFile(file)) {
//    			err.println("The selected file is not OMG IDL format!");
//    			return null;
//            }
            String omgidlFile = file.getLocationURI().toString();
            String outputDir = file.getParent().getLocation().toString();

    		// load OMG IDL file
            out.println("Loading OMG IDL file '" + omgidlFile + "' ...");
            OMGIDLConnector conn = new OMGIDLConnector();
    		OMGIDLModelContainer omgidl = (OMGIDLModelContainer)conn.loadModel(omgidlFile);
//    		if (omgidl==null) {
//    			err.println("Couldn't load OMG IDL file '" + omgidlFile + "'.");
//    			return null;
//    		}
            for (TranslationUnit tu : ListExtensions.reverseView(omgidl.models())) {
	    		if (tu==null) {
	    			err.println("Error during load of OMG IDL file '" + omgidlFile + "'.");
	    			return null;
	    		}
	    		out.println("OMG IDL: loaded translation unit '" + tu.getIdentifier() + "'");
    		
	    		// transform OMG IDL to Franca
	    		out.println("Transforming to Franca IDL model ...");
	    		Map<FModel, String> result = null;
	    		try {
	    			result = conn.toFrancas(omgidl);
	    			out.println(IssueReporter.getReportString(conn.getLastTransformationIssues()));    			
	    		} catch (Exception e) {
	    			// print stack trace to stdout to ease debugging
	    			e.printStackTrace();
	    			
	    			// print explanation and stack trace to console
	    			err.println("Exception during transformation: " + e.toString());
	    			for(StackTraceElement f : e.getStackTrace()) {
	    				err.println("\tat " + f.toString());
	    			}
	    			err.println("Internal transformation error, aborting.");
					return null;
	    		}
    		
	    		// save Franca fidl file
	    		int ext = file.getName().lastIndexOf("." + file.getFileExtension());
	    		try {
	    			// save all transformed files
	    			for(FModel fmodel : result.keySet()) {
	    	    		String outfile = result.get(fmodel) + ".fidl";
	    	    		String outpath = outputDir + "/" + outfile;
	    	    		if (saver.saveModel(fmodel, outpath)) {
	    	    			out.println("Saved Franca IDL file '" + outpath + "'.");
	    	    		} else {
	    	    			err.println("Franca IDL file couldn't be written to file '" + outpath + "'.");
	    	    		}
	    			}
	    		} catch (Exception e) {
	    			err.println("Exception while persisting result model to file: " + e.toString());
	    			for(StackTraceElement f : e.getStackTrace()) {
	    				err.println("\tat " + f.toString());
	    			}
	    			err.println("Internal transformation error, aborting.");
					return null;
	    		}
            }
    		// refresh IDE (in order to make new files visible)
            IProject project = file.getProject();
            try {
    			project.refreshLocal(IResource.DEPTH_INFINITE, null);;
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
		return null;
	}
}

