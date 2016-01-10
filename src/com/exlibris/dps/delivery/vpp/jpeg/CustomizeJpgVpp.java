package com.exlibris.dps.delivery.vpp.jpeg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.exlibris.core.sdk.formatting.DublinCore;
import com.exlibris.core.sdk.parser.IEParserException;
import com.exlibris.digitool.common.dnx.DNXConstants;
import com.exlibris.digitool.common.dnx.DnxDocument;
import com.exlibris.digitool.common.streams.ScriptUtil;
import com.exlibris.dps.sdk.access.Access;
import com.exlibris.dps.sdk.access.AccessException;
import com.exlibris.dps.sdk.delivery.AbstractViewerPreProcessor;
import com.exlibris.dps.sdk.delivery.SmartFilePath;
import com.exlibris.dps.sdk.deposit.IEParser;


public class CustomizeJpgVpp extends AbstractViewerPreProcessor{

	private static final String RESIZE_COMMAND = "-resize";
	private static final String CONVERT = "convert";
	private static final int HUNDRED_KILO = 100000;
	private static final int FIFTY_MEGA = 50000000;
	private static final int HALF_GIGA = 500000000;
	private static final int GIGA = 1000000000;
	private static final String IS_MOBILE = "is_mobile";
	private String filePid = null;
	Access access;

	//This method will be called by the delivery framework before the call for the execute Method
	@Override
	public void init(DnxDocument dnx, Map<String, String> viewContext, HttpServletRequest request, String dvs,String ieParentId, String repDirName)
			throws AccessException {
		super.init(dnx, viewContext, request, dvs, ieParentId, repDirName);
        this.filePid = getPid();
	}

	//No need for progress bar since this is a File viewer VPP.
	public boolean runASync(){
		return false;
	}

	//Does the pre-viewer processing tasks.
	public void execute() throws Exception {

		convertFile();

		//Set the Delivery Access with parameters the JPG viewer will need.
        Map<String, Object> paramMap = getAccess().getParametersByDVS(getDvs());
        paramMap.put("file_pid", filePid);
        paramMap.put("rep_pid", repDirName);
        paramMap.putAll(getViewContext());
        getAccess().setParametersByDVS(getDvs(), paramMap);
	}

	private void convertFile() throws Exception{

		String dnxDocument = getAccess().getFileInfoByDVS(dvs, filePid).toString();

		//STEP 1: Export the file to a temp directory so we can modify it for delivery
		String filePath = getAccess().exportFileStream(filePid, CustomizeJpgVpp.class.getSimpleName(), ieParentId, repDirName, null, dnxDocument, getDvs());

		//STEP 2: Set the Delivery Access with the exported file path in order to allow the JPG Viewer to use the modified file.
		getAccess().setFilePathByDVS(getDvs(), new SmartFilePath(filePath), filePid);

		//STEP 3: calculate the resizing percentage we wish to use for the JPG conversion based on the files DNX/DC metadata
		String resize = getResizingPercentage();

		//STEP 4: create and run the Image Magic Script:
		//Creat an array of arguments for the script
		List<String> args = new ArrayList<String>();
		args.add(filePath);
		args.add(RESIZE_COMMAND);
		args.add(resize);
		args.add(filePath);
        try {
        	//run the script using the Rosetta ScriptUtil
            ScriptUtil.runScript(CONVERT, args);
        } catch (Exception e) {
        	System.err.println("Failed converting file: " + filePid);
        	e.printStackTrace();
        }
	}


	/*
	 * Dummy function for resizing the file based on the "FILESIZEBYTES" property in the file's DNX
	 */
	private String getResizingPercentage() throws Exception {

		//If the delivery device is mobile- always resize to 20%
		Boolean isMobile = Boolean.valueOf(""+getAccess().getParametersByDVS(getDvs()).get(IS_MOBILE));
		if(isMobile.booleanValue()) {
			System.out.println("resizing file: " + filePid + " to 20% of its original size for delivery.");
			return "20%";
		}

		//For regular devices, resize the file based on the file's metadata
		Long fileSize = Long.parseLong(getDnx().getSectionKeyValue(DNXConstants.GENERALFILECHARACTERISTICS.FILESIZEBYTES));
		String resize = "80%";
		if(fileSize > GIGA) {
			resize = "10%";
		} else if (fileSize > HALF_GIGA) {
			resize = "20%";
		} else if (fileSize > FIFTY_MEGA) {
			resize = "40%";
		} else if (fileSize > HUNDRED_KILO) {
			resize = "60%";
		}

		System.out.println("resizing file: " + filePid + " to " + resize + " of its original size for delivery.");
		return resize;
	}

	/*
	 * Example for retrieving Dublin Core Metadata Values:
	 */
	private void getDCValuesExample() throws Exception, IEParserException {
		IEParser ieParser = getAccess().getIEByDVS(dvs);
		DublinCore dc = ieParser.getIeDublinCore();
		dc.getDcValue("title");
		dc.getDctermsValue("isPartOf");
	}
}
