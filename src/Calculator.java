import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;



public class Calculator {
	private int NUMBER_OF_PROCESSORS = 30;
	
	public static void main(String[] args) throws IOException {
		Calculator self = new Calculator();
		String dataType = args[0];
		String metricType = args[1];
		String categorySize = args[2];
		System.out.println("data type is : " + dataType);
		if(metricType.contains("Modal"))
			self.computemodalattr_entries(dataType, metricType, categorySize);
		else if(metricType.contains("Time"))
			self.computetimeattr_entries(dataType, metricType, categorySize);
		else if(metricType.contains("regenerate-scarcity"))
			self.computescarcity_entries();
		else if(metricType.contains("Ensemble"))
			self.computeEnsemble_entries(dataType, metricType, categorySize);
		else if(metricType.contains("Spatial"))
			self.computeSpatial_entries(dataType);
//		String type = "TimeStd";
//		String type = "ModalMean";
		
	}
	
	////////////////////////////////////Spatial Attributes////////////////////////////////////////////////
	public void computeSpatial_entries(String dataType) throws IOException{
		String targetDir = "/work/asu/data/CalculationResults/" + dataType + "/SpatialStat/";
		String srcBasisDir = "/work/asu/data/CalculationResults/" + dataType + "/TimeMean/";
		
		ArrayList<File> sources = new ArrayList<File>();
		sources = getAllFiles(srcBasisDir, sources);
		if(sources.isEmpty()){
			System.out.println("source files are empty!");
			return;
		}
		
		ArrayList<TiffParser> parsers = new ArrayList<TiffParser>();
		parsers = parseFilesThread(sources, parsers);
		if(parsers.isEmpty()){
			System.out.println("Parse set is empty!");
			return;
		}
		String[] metricList = {"Mean", "Std", "CV", "IQR"};
		for(TiffParser eachparser : parsers){
			double[] minmax = eachparser.getMinmax();
			String[] filename = eachparser.getFilePath().split("/");
			String modelname = filename[filename.length-1].replace(".tif", "");
			String targetPath = targetDir + modelname + ".txt";
			String text = "";
			CalcStatWithoutThread[] statService = new CalcStatWithoutThread[metricList.length];
			Thread[] statServerThread = new Thread[metricList.length];
			for(int i=0; i<metricList.length; i++){
				statService[i] = new CalcStatWithoutThread(dataType, metricList[i], minmax, eachparser.getData());
				statServerThread[i] = new Thread(statService[i]);
				statServerThread[i].start();
			}
			try{
				for(int i=0; i<metricList.length; i++){
					statServerThread[i].join();
					System.out.println(i + " Finished~");
				}
			} catch (InterruptedException e){
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			for(int i=0; i<metricList.length; i++){
				if(text == "")
					text = modelname + " " + metricList[i] + " " + String.valueOf(statService[i].getResult()) + "\n";
				else
					text += modelname + " " + metricList[i] + " " + String.valueOf(statService[i].getResult()) + "\n";
			} 
			try(BufferedWriter br = new BufferedWriter(new FileWriter(targetPath))){
				br.write(text);
				br.close();
			}
		}
	}
	
	////////////////////////////////////Ensemble Attributes////////////////////////////////////////////////
	public void computeEnsemble_entries(String dataType, String metricType, String sourceType){
		String targetDir = "/work/asu/data/CalculationResults/" + dataType + "/EnsembleStatOf" + sourceType + "/";
		String srcBasisDir = "/work/asu/data/CalculationResults/" + dataType + "/" + sourceType + "/";
		ArrayList<File> sources = new ArrayList<File>();
		sources = getAllFiles(srcBasisDir, sources);
		if(sources.isEmpty()){
			System.out.println("source files are empty!");
			return;
		}
		ArrayList<TiffParser> parsers = new ArrayList<TiffParser>();
		parsers = parseFilesThread(sources, parsers);
		if(parsers.isEmpty()){
			System.out.println("Parse set is empty!");
			return;
		}
		double[] size = parsers.get(0).getSize();
		double[] bufferSet = new double[(int) (size[0]*size[1])];
		singletypecomputationonscarcity(dataType, metricType, parsers, bufferSet);
		String outputfile = targetDir + metricType + "Of" + sourceType + ".tif";
		saveTiff(parsers.get(0), outputfile, bufferSet);
	}
	
	////////////////////////////////////Time Attributes////////////////////////////////////////////////
	public void computetimeattr_entries(String dataType, String metricType, String categorySize) throws FileNotFoundException{
		String targetDir = "/work/asu/data/CalculationResults/" + dataType + "/" + metricType + "/";
		String srcDir = "/work/asu/data/" + dataType + "/";
		if(dataType.equals("Runoff"))
			srcDir = "/work/asu/data/wsupply/BW_1km/historical/";
		ArrayList<File> allsources = new ArrayList<File>();
		allsources = getAllFiles(srcDir, allsources);
		HashMap<String, ArrayList<File>>modal2files = new HashMap<String, ArrayList<File>>();
		for(File each : allsources){
			String[] name = each.getName().replace(".tif", "").split("_");
			if(!each.getName().contains("ClimMean")){
				int startIndex = 4;
				if(!dataType.contains("Scarcity"))
					startIndex = 0;
				String[] model = Arrays.copyOfRange(name, startIndex, name.length-2);
				String tModal = "";
				for(String str : model){
					if(tModal!="")
						tModal = tModal+"_"+str;
					else
						tModal = str;
				}
				ArrayList<File> files = new ArrayList<File>();
				if(modal2files.containsKey(tModal))
					files = modal2files.get(tModal);
				files.add(each);
				modal2files.put(tModal, files);
			}
		}
		
		for(String key : modal2files.keySet()){
			ArrayList<File> files = modal2files.get(key);
			computeTimeAttr(dataType, key, metricType, files, targetDir, categorySize);
		}
	}
	
	public boolean computeTimeAttr(String dataType, String key, String metricType, ArrayList<File> files, String targetDir, String categorySize) throws FileNotFoundException{
		if(files.isEmpty())
			return false;
		ArrayList<TiffParser> parsers = new ArrayList<TiffParser>();
		parsers = parseFilesThread(files, parsers);
		
		double[] sSize = parsers.get(0).getSize();
		int tgtHeight = (int)sSize[0];
		int tgtWidth = (int)sSize[1];
		if(metricType.contains("Area")){
			if(dataType.contains("Scarcity"))
				categorySize = "4";
			double[] AreaTypeBuffer = new double[Integer.valueOf(categorySize)];
			computeAreawithoutThreads(dataType, metricType, parsers, AreaTypeBuffer);
			String targetPath = targetDir + key + "_" + metricType + "_" + categorySize + ".txt";
			File targetFile = new File(targetPath);
			if(targetFile.exists()){
				targetFile.delete();
			}
			try (PrintStream out = new PrintStream(new FileOutputStream(targetPath))) {
				String text  = "";
				for(int i=0; i<AreaTypeBuffer.length; i++){
					if(text == "")
						text += String.valueOf(AreaTypeBuffer[i]);
					else
						text = text + " " + String.valueOf(AreaTypeBuffer[i]);
				}
			    out.print(text);
			}
			return true;
		}
		else{
			double[] bufferSet = new double[tgtHeight*tgtWidth];
			singletypecomputationonscarcity(dataType, metricType, parsers, bufferSet);
			String outputfile = targetDir + key + "_" + metricType + ".tif";
			saveTiff(parsers.get(0), outputfile, bufferSet);
			return true;			
		}

	}
	
	
	////////////////////////////////////Modal Attributes////////////////////////////////////////////////
	public void computemodalattr_entries(String dataType, String metricType, String categorySize){
		String targetDir = "/work/asu/data/CalculationResults/" + dataType + "/" + metricType + "/";
		String srcDir = "/work/asu/data/" + dataType + "/";
		if(dataType.equals("Runoff"))
			srcDir = "/work/asu/data/wsupply/BW_1km/historical/";
		ArrayList<File> allsources = new ArrayList<File>();
		allsources = getAllFiles(srcDir, allsources);
		HashMap<String, ArrayList<File>>year2files = new HashMap<String, ArrayList<File>>();
		for(File each : allsources){
			String[] name = each.getName().replace(".tif", "").split("_");
			if(!each.getName().contains("ClimMean")){
				String year = name[name.length-1];
				if(year2files.containsKey(year)){
					ArrayList<File> files = year2files.get(year);
					files.add(each);
					year2files.put(year, files);
				}
				else{
					ArrayList<File> files = new ArrayList<File>();
					year2files.put(year, files);
				}
			}
		}
		for(String key : year2files.keySet()){
			ArrayList<File> files = year2files.get(key);
			comptueModalAttr(dataType, key, metricType, files, targetDir);
		}
	}
	
	public boolean comptueModalAttr(String dataType, String key, String metricType, ArrayList<File> files, String targetDir){
		if(files.isEmpty()){
			System.out.println("Time Mean files are empty!");
			return false;
		}
		ArrayList<TiffParser> parsers = new ArrayList<TiffParser>();
		parsers = parseFilesThread(files, parsers);
		
		double[] sSize = parsers.get(0).getSize();
		int tgtHeight = (int)sSize[0];
		int tgtWidth = (int)sSize[1];
		double[] bufferSet = new double[tgtHeight*tgtWidth];
		singletypecomputationonscarcity(dataType, metricType, parsers, bufferSet);
//		write geotiff files
//		String partName = files.get(0).getName().replace(".tif", "");
		String outputfile = targetDir + key + "_" + metricType + ".tif";
		saveTiff(parsers.get(0), outputfile, bufferSet);
		return true;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////Public Modules////////////////////////////////////////////////
//	modules for operating calculation based on the type on scarcity files only
	public boolean singletypecomputationonscarcity(String dataType, String type, ArrayList<TiffParser> parsers, double[] bufferSet){
		if(parsers.isEmpty()){
			return false;
		}
		double[] sSize = parsers.get(0).getSize();
		int tgtHeight = (int)sSize[0];
		int tgtWidth = (int)sSize[1];
		double[] globalMinmax = {99999999, 0};
		for(TiffParser parser : parsers){
			double[] _minmax = parser.getMinmax();
			if(_minmax[0]!=-1 && _minmax[1]!=-1){
				if(_minmax[0]<globalMinmax[0])
					globalMinmax[0] = _minmax[0];
				if(_minmax[1]>globalMinmax[1])
					globalMinmax[1] = _minmax[1];
			}
		}
		CalcAttributesThread[] statService = new CalcAttributesThread[NUMBER_OF_PROCESSORS];
		Thread[] statServerThread = new Thread[NUMBER_OF_PROCESSORS];
		int delta = tgtHeight/NUMBER_OF_PROCESSORS;
		for(int i=0; i<NUMBER_OF_PROCESSORS; i++){
			int h1 = i * delta;
			int h2 = (i+1) * delta;
			int startIndex = h1 * tgtWidth;
			int endIndex =  h2 * tgtWidth;
			statService[i] = new CalcAttributesThread(dataType, type, startIndex, endIndex, parsers, bufferSet, globalMinmax);
			statServerThread[i] = new Thread(statService[i]);
			statServerThread[i].start();
		}
		try{
			for(int i=0; i<NUMBER_OF_PROCESSORS; i++){
				statServerThread[i].join();
				System.out.println(i + " Finished~");
			}
		} catch (InterruptedException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(" Finished~");
		return true;
	}
	
//	module for saving tiff files
	public boolean saveTiff(TiffParser template, String outputfile, double[] bufferSet){
		Driver driver = gdal.GetDriverByName("GTiff");
		Dataset dst_ds = driver.Create(outputfile, (int)template.getSize()[1], (int)template.getSize()[0], 1, gdalconst.GDT_Float64);
		dst_ds.SetGeoTransform(template.getGeoInfo());
		dst_ds.SetProjection(template.getProjRef());
		int writingResult = dst_ds.GetRasterBand(1).WriteRaster(0, 0, (int)template.getSize()[1], (int)template.getSize()[0], bufferSet);
		dst_ds.FlushCache();
		dst_ds.delete();
		System.out.println("Writing geotiff result is: " + writingResult);	
		return true;
	}
	
	// module for parsing tiff files
	public ArrayList<TiffParser> parseFilesThread(ArrayList<File> files, ArrayList<TiffParser> parsers){
		LoadTiffThread[] service = new LoadTiffThread[files.size()];
		Thread[] serverThread = new Thread[files.size()];
		for(int i=0; i<files.size(); i++){
			String filePath = files.get(i).getAbsolutePath();
			service[i] = new LoadTiffThread(filePath);
			serverThread[i] = new Thread(service[i]);
			serverThread[i].start();
		}
		
		try {
			for(int i=0; i<files.size(); i++){
				serverThread[i].join();
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(int i=0; i<files.size(); i++){
			parsers.add(service[i].getResult());
		}
		return parsers;
	}
	
	// module for getting all files
	public ArrayList<File> getAllFiles(String directoryName, ArrayList<File> files) {
	    File directory = new File(directoryName);

	    // get all the files from a directory
	    File[] fList = directory.listFiles();
	    for (File file : fList) {
	    	String name = file.getName();
	        if (file.isFile() && name.endsWith(".tif") && !name.contains("MPI-ESM-LR_CCLM") && !name.contains("HadGEM2-ES_CCLM") && !name.contains("EC-EARTH-r12_CCLM")
					&& !name.contains("CNRM-CM5_CCLM") && !name.contains("EC-EARTH-r3_HIRHAM")) {
	            files.add(file);
	        } else if (file.isDirectory()) {
	        	getAllFiles(file.getAbsolutePath(), files);
	        }
	    }
	    return files;
	}
	
	// module for getting all supplies with keywords
	public ArrayList<String> getAllSupplies(String supplyDir, String year) throws IOException{
		ArrayList<File> supplyListOfFiles = new ArrayList<File>();
		supplyListOfFiles = getAllFiles(supplyDir, supplyListOfFiles); 
		ArrayList<String> supplyPathList = new ArrayList<String>();
		for (int j = 0; j < supplyListOfFiles.size(); j++) {
			if (supplyListOfFiles.get(j).isFile()) {
				String supplyFiles = supplyListOfFiles.get(j).getName();
				if(!supplyFiles.contains("MPI-ESM-LR_CCLM") && !supplyFiles.contains("HadGEM2-ES_CCLM") && !supplyFiles.contains("EC-EARTH-r12_CCLM")
						&& !supplyFiles.contains("CNRM-CM5_CCLM") && !supplyFiles.contains("EC-EARTH-r3_HIRHAM") && supplyFiles.contains(year)){
					String supplyPath = supplyListOfFiles.get(j).getAbsolutePath();
					supplyPathList.add(supplyPath);							
				}
			}
		}
		return supplyPathList;
	}
	
	//	function for computing the area probabilty without multi-threads
	public boolean computeAreawithoutThreads(String dataType, String metricType, ArrayList<TiffParser> parsers, double[] categoryBuffer){
		if(parsers.isEmpty()){
			return false;
		}
		double[] sSize = parsers.get(0).getSize();
		int tgtHeight = (int)sSize[0];
		int tgtWidth = (int)sSize[1];
		double[] globalMinmax = {99999999, 0};
		for(TiffParser parser : parsers){
			double[] _minmax = new double[2];
			parser.GetMinMax(_minmax);
			if(_minmax[0]!=-1 && _minmax[1]!=-1){
				if(_minmax[0]<globalMinmax[0])
					globalMinmax[0] = _minmax[0];
				if(_minmax[1]>globalMinmax[1])
					globalMinmax[1] = _minmax[1];
			}
		}
//		double totalArea = 0;
		for(int h=0; h<tgtHeight; h++){
			for(int w=0; w<tgtWidth; w++){
				int index = w + h*tgtWidth;
				double[] pr = new double[categoryBuffer.length];
				for(int x=0; x<parsers.size(); x++){
					double val = parsers.get(x).getData()[index];
					if(val!=-1 && !Double.isNaN(val)){
//						totalArea++;
						if(dataType.contains("Scarcity")){
							pr[(int) (categorizeScarcity(val)-1)]++;
						}
						else{
							int catIndex = (int) ((val-globalMinmax[0])/(globalMinmax[1]-globalMinmax[0]+1)*categoryBuffer.length);
							pr[catIndex]++;
						}
					}
				}
				for(int y=0; y<categoryBuffer.length; y++){
//					here we assume all pixels would have NaN together or none of them is NaN
					categoryBuffer[y] += pr[y]/parsers.size();
				}
			}
		}

//		totalArea = totalArea/parsers.size();
//		for(int i=0; i<categoryBuffer.length; i++){
//			categoryBuffer[i] = categoryBuffer[i];
//		}
		
		return true;
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////
	
	
	////////////////////////////////////Modal mean from original supplies and demand////////////////////////////////////////////////
	public void computescarcity_entries() throws IOException{
		String targetDir = "/work/asu/data/Scarcity/";
		String supplySrcDir = "/work/asu/data/wsupply/BW_1km/historical/";
		String demandSrcDir = "/work/asu/data/wdemand/popden_pred/";
		ArrayList<File> alldemands = new ArrayList<File>();
		alldemands = getAllFiles(demandSrcDir, alldemands);
		
		for(File eachdemand : alldemands){
			if(eachdemand.getName().contains("OECD_SSP1")){
				String[] splitted = eachdemand.getName().replace(".tif", "").split("_");
				String year = splitted[splitted.length-1];
				System.out.println("year is:" + year);
				ArrayList<String> allsupplies = new ArrayList<String>();
				allsupplies = getAllSupplies(supplySrcDir, year);
				for(String eachsupply : allsupplies){
					String[] eachsupplyArr = eachsupply.split("/");
					String targetFile = targetDir + eachdemand.getName().replace(".tif", "") + "_" + eachsupplyArr[eachsupplyArr.length-1];
					computeScarcity(eachdemand.getAbsolutePath(), eachsupply, targetFile);
				}
			}
		}
	}
	
	public boolean computeScarcity(String dPath, String sPath, String outputfile){
		TiffParser dParser = new TiffParser(dPath);
		TiffParser sParser = new TiffParser(sPath);
		if(sParser.parseSuccess && dParser.parseSuccess){
			int tgtHeight = (int)sParser.getSize()[0];
			int tgtWidth = (int)sParser.getSize()[1];
			int deltaX = 0;
			int deltaY = 0;
			if(dParser.getSize()[0] != tgtHeight || dParser.getSize()[1] != tgtWidth){
				deltaX = (int) (dParser.getSize()[1] - tgtWidth);
				deltaY = (int) (dParser.getSize()[0] - tgtHeight);
			}
			System.out.println("dSize[0] is " + dParser.getSize()[1] + " dSize[1] is "+ dParser.getSize()[0]);
			double[] bufferSet = new double[tgtHeight*tgtWidth];
			double ratio = 1/1000.0;
			CalcModalAttributesThread[] statService = new CalcModalAttributesThread[NUMBER_OF_PROCESSORS];
			Thread[] statServerThread = new Thread[NUMBER_OF_PROCESSORS];
			int delta = tgtHeight/NUMBER_OF_PROCESSORS;
			String type = "scarcity";
			for(int i=0; i<NUMBER_OF_PROCESSORS; i++){
				int h1 = i * delta;
				int h2 = (i+1) * delta;
				statService[i] = new CalcModalAttributesThread(type, ratio, dParser.getData(), sParser, h1, h2, deltaX, deltaY, tgtWidth, bufferSet);
				statServerThread[i] = new Thread(statService[i]);
				statServerThread[i].start();
			}
			try{
				for(int i=0; i<NUMBER_OF_PROCESSORS; i++){
					statServerThread[i].join();
					System.out.println(i + " Finished~");
				}
			} catch (InterruptedException e){
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			System.out.println(" Finished~");
//			write geotiff files
			Driver driver = gdal.GetDriverByName("GTiff");
			Dataset dst_ds = driver.Create(outputfile, (int)tgtWidth, (int)tgtHeight, 1, gdalconst.GDT_Float64);
			dst_ds.SetGeoTransform(sParser.getGeoInfo());
			dst_ds.SetProjection(sParser.getProjRef());
			int writingResult = dst_ds.GetRasterBand(1).WriteRaster(0, 0, (int)tgtWidth, (int)tgtHeight, bufferSet);
			dst_ds.FlushCache();
			dst_ds.delete();
			System.out.println("Writing geotiff result is: " + writingResult);	
			return true;
		}
		return false;
	}
	
	public boolean computeAndsave(String dPath, ArrayList<String> sPathList, String outputfile) throws IOException{
		if(sPathList.isEmpty())	{
			System.out.println("supply path is empty, so it cannot compute and save!");
			return false;
		}
		System.out.println("demand path is:" + dPath);
		TiffParser dParser = new TiffParser();
		dParser.setFilePath(dPath);
		ArrayList<TiffParser> sParserArr = new ArrayList<TiffParser>();
		double[] sSize = {};
		
		LoadTiffThread[] service = new LoadTiffThread[sPathList.size()];
		Thread[] serverThread = new Thread[sPathList.size()];
		for(int i=0; i<sPathList.size(); i++){
			String filePath = sPathList.get(i);
			service[i] = new LoadTiffThread(filePath);
			serverThread[i] = new Thread(service[i]);
			serverThread[i].start();
		}
		
		try {
			for(int i=0; i<sPathList.size(); i++){
				serverThread[i].join();
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(int i=0; i<sPathList.size(); i++){
			sParserArr.add(service[i].getResult());
		}
		System.out.println("Program Ends!");
		sSize = sParserArr.get(0).getSize();
		System.out.println("Supply Size is:" + sSize[0] + " , " + sSize[1]);
		if(dParser.parser() && !sParserArr.isEmpty()){
			int tgtHeight = (int)sSize[0];
			int tgtWidth = (int)sSize[1];
			int deltaX = 0;
			int deltaY = 0;
			if(dParser.getSize()[0] != tgtHeight || dParser.getSize()[1] != tgtWidth){
				deltaX = (int) (dParser.getSize()[1] - tgtWidth);
				deltaY = (int) (dParser.getSize()[0] - tgtHeight);
			}
			System.out.println("dSize[0] is " + dParser.getSize()[1] + " dSize[1] is "+ dParser.getSize()[0]);
			double[] bufferSet = new double[tgtHeight*tgtWidth];
			double ratio = 1/1000.0;
//			System.out.println("Ratio is:" + ratio);
			CalcModalAttributesThread[] statService = new CalcModalAttributesThread[NUMBER_OF_PROCESSORS];
			Thread[] statServerThread = new Thread[NUMBER_OF_PROCESSORS];
			int delta = tgtHeight/NUMBER_OF_PROCESSORS;
			String type = "multimodal-mean";
			for(int i=0; i<NUMBER_OF_PROCESSORS; i++){
				int h1 = i * delta;
				int h2 = (i+1) * delta;
//				int startIndex = h1 * tgtWidth;
//				int endIndex =  h2 * tgtWidth;
				statService[i] = new CalcModalAttributesThread(type, ratio, dParser.getData(), sParserArr, h1, h2, deltaX, deltaY, tgtWidth, bufferSet);
				statServerThread[i] = new Thread(statService[i]);
				statServerThread[i].start();
			}
			try{
				for(int i=0; i<NUMBER_OF_PROCESSORS; i++){
					statServerThread[i].join();
					System.out.println(i + " Finished~");
				}
			} catch (InterruptedException e){
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println(" Finished~");
//			write geotiff files
			Driver driver = gdal.GetDriverByName("GTiff");
			Dataset dst_ds = driver.Create(outputfile, (int)tgtWidth, (int)tgtHeight, 1, gdalconst.GDT_Float64);
			dst_ds.SetGeoTransform(sParserArr.get(0).getGeoInfo());
			dst_ds.SetProjection(sParserArr.get(0).getProjRef());
			int writingResult = dst_ds.GetRasterBand(1).WriteRaster(0, 0, (int)tgtWidth, (int)tgtHeight, bufferSet);
			dst_ds.FlushCache();
			dst_ds.delete();
			System.out.println("Writing geotiff result is: " + writingResult);	
		}
		else{
			System.out.println("parse Error in path!");
			return false;
		}
		return true;
	}
	
//	output continuous scarcity value to categories from 1~4
	private double categorizeScarcity(double scarcity){
		if(scarcity<=500 && scarcity>=0) {return 1;}
		else if(scarcity>500 && scarcity<=1000) {return 2;}
		else if(scarcity>1000 && scarcity<=1700) {return 3;}
		else if(scarcity>1700)	{return 4;}
		return -1;
	}
}
