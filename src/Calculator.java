import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;



public class Calculator {
	private int NUMBER_OF_PROCESSORS = 30;
	
	public static void main(String[] args) throws IOException {
		Calculator self = new Calculator();
		self.computescarcity_entries();
	}
	
	public void computetimemean(){
		String targetDir = "/work/asu/data/CalculationResults/TimeMean/";
		String scarcitySrcDir = "/work/asu/data/CalculationResults/Scarcity/";
		
	}
	
	public void computescarcity_entries() throws IOException{
		String targetDir = "/work/asu/data/CalculationResults/Scarcity/";
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
			CalcStatThread[] statService = new CalcStatThread[NUMBER_OF_PROCESSORS];
			Thread[] statServerThread = new Thread[NUMBER_OF_PROCESSORS];
			int delta = tgtHeight/NUMBER_OF_PROCESSORS;
			String type = "scarcity";
			for(int i=0; i<NUMBER_OF_PROCESSORS; i++){
				int h1 = i * delta;
				int h2 = (i+1) * delta;
				statService[i] = new CalcStatThread(type, ratio, dParser.getData(), sParser, h1, h2, deltaX, deltaY, tgtWidth, bufferSet);
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
			CalcStatThread[] statService = new CalcStatThread[NUMBER_OF_PROCESSORS];
			Thread[] statServerThread = new Thread[NUMBER_OF_PROCESSORS];
			int delta = tgtHeight/NUMBER_OF_PROCESSORS;
			String type = "multimodal-mean";
			for(int i=0; i<NUMBER_OF_PROCESSORS; i++){
				int h1 = i * delta;
				int h2 = (i+1) * delta;
//				int startIndex = h1 * tgtWidth;
//				int endIndex =  h2 * tgtWidth;
				statService[i] = new CalcStatThread(type, ratio, dParser.getData(), sParserArr, h1, h2, deltaX, deltaY, tgtWidth, bufferSet);
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
	
	public ArrayList<File> getAllFiles(String directoryName, ArrayList<File> files) {
	    File directory = new File(directoryName);

	    // get all the files from a directory
	    File[] fList = directory.listFiles();
	    for (File file : fList) {
	        if (file.isFile() && file.getName().endsWith(".tif")) {
	            files.add(file);
	        } else if (file.isDirectory()) {
	        	getAllFiles(file.getAbsolutePath(), files);
	        }
	    }
	    return files;
	}
	
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
}