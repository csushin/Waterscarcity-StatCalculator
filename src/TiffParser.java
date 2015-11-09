

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.gdalconst.gdalconstConstants;

public class TiffParser {
	private double[] lrLatlng;
	private double[] ulLatlng;
	private double[] pixelHW;
	private double[] geoUnitPerUW;
	private double[] statistics;
	private double[] data;
	private double[] size;
	public boolean parseSuccess = false;
	
	
	private String filePath;
	private int xoff, yoff, xSize, ySize;
	private double[] geoInfo;
	private String projRef;
	
	public String getProjRef() {
		return projRef;
	}

	public void setProjRef(String projRef) {
		this.projRef = projRef;
	}

	public double[] getGeoInfo() {
		return geoInfo;
	}

	public void setGeoInfo(double[] geoInfo) {
		this.geoInfo = geoInfo;
	}

	TiffParser(){
	}
	
	public TiffParser(String filePath){
		this.setFilePath(filePath);
		this.parser();
		this.parseSuccess = true;
	}
	
	TiffParser(int xoff, int yoff, int xSize, int ySize){
		this.xoff = xoff;
		this.yoff = yoff;
		this.xSize = xSize;
		this.ySize = ySize;
	}
	
	public int getXoff() {
		return xoff;
	}

	public void setXoff(int xoff) {
		this.xoff = xoff;
	}

	public int getYoff() {
		return yoff;
	}

	public void setYoff(int yoff) {
		this.yoff = yoff;
	}

	public int getxSize() {
		return xSize;
	}

	public void setxSize(int xSize) {
		this.xSize = xSize;
	}

	public int getySize() {
		return ySize;
	}

	public void setySize(int ySize) {
		this.ySize = ySize;
	}

	public boolean parser(){
		boolean opened = false;
		String fileName_tif = filePath;  
		gdal.AllRegister();
		Dataset hDataset = gdal.Open(fileName_tif, gdalconstConstants.GA_ReadOnly);
		if (hDataset == null)
		{
			// parse Tiff Image
			System.err.println("GDALOpen failed - " + gdal.GetLastErrorNo());
			System.err.println(gdal.GetLastErrorMsg());
			opened = false;
			return opened;
		}
		
			Driver hDriver = hDataset.GetDriver();
			Band hBand = hDataset.GetRasterBand(1);
			System.out.println("Description:" + hDataset.GetDescription());
			int xSize = hDataset.getRasterXSize();
			int ySize = hDataset.getRasterYSize();
			double[] sizeArr = {ySize, xSize};
			this.size = sizeArr;
			

			double[] pixelData = new double[xSize * ySize];
			int err = hBand.ReadRaster(0, 0, xSize, ySize, gdalconst.GDT_Float64, pixelData);
			if(err==gdalconst.CE_Failure)
			{
				System.out.println("Getting Data Error! An Error occured in getting pixel value in Dissolved One!");
			}				

			System.out.println("Driver: " + hDriver.getShortName() + "/"
					+ hDriver.getLongName());

			System.out.println("Size is " + xSize + ", " + ySize);

			// get geo information of tiff
			double[] geoInfo = hDataset.GetGeoTransform();
			String projRef = hDataset.GetProjectionRef();
//			System.out.println(geoInfo[0]);// upper-left longitude
//			System.out.println(geoInfo[1]);//pixel width, lng
//			System.out.println(geoInfo[2]);
//			System.out.println(geoInfo[3]);// upper-left latitude
//			System.out.println(geoInfo[4]);
//			System.out.println(geoInfo[5]);// pixel height, lat

			// get statistical information about tiff
			double[] dfMinMax = new double[2];
			double[] dfMax = new double[1];
			double[] dfMean = new double[1];
			double[] dfstddev = new double[1];
//			int eErr = hBand.GetStatistics(true, true, dfMin, dfMax, dfMean, dfstddev);
//			if(eErr==gdalconst.CE_None)
//				System.out.println("Getting Statistics Success!");
//			else if(eErr==gdalconst.CE_Warning)
//				System.out.println("Getting Statistics Warning! No Data Returned for statistics");
//			else if(eErr==gdalconst.CE_Failure)
//				System.out.println("Getting Statistics Error! An Error occured in getting statistics!");
			
			hDataset.delete();
			
			// deliver the value to the class variables
			this.ulLatlng = new double[2];
			this.ulLatlng[0] = geoInfo[3]+geoInfo[5]*this.yoff*ySize;//lat
			this.ulLatlng[1] = geoInfo[0]+geoInfo[1]*this.xoff*xSize;//lon
			this.lrLatlng = new double[2];
			this.lrLatlng[0] = this.ulLatlng[0] + geoInfo[5]*ySize;//lat
			this.lrLatlng[1] = this.ulLatlng[1] + geoInfo[1]*xSize;//lon
			System.out.println(this.xoff);
			System.out.println(this.yoff);
			System.out.println(this.ulLatlng[0]);
			System.out.println(this.ulLatlng[1]);
			System.out.println(this.lrLatlng[0]);
			System.out.println(this.lrLatlng[1]);
			this.pixelHW = new double[2];
			this.pixelHW[0] = ySize;
			this.pixelHW[1] = xSize;
			this.geoUnitPerUW = new double[2];
			this.geoUnitPerUW[0] = geoInfo[5];
			this.geoUnitPerUW[1] = geoInfo[1];
			this.statistics = new double[4];
			this.statistics[0] = dfMinMax[0];
			this.statistics[1]= dfMinMax[1];
			this.geoInfo = geoInfo;
			this.projRef = projRef;
			
//			this.statistics[2] = dfMean[0];
//			this.statistics[3] = dfstddev[0];
			this.data = new double[xSize*ySize];
			for(int i=0; i<xSize*ySize; i++){
//				if(pixelData[i]!=0)
//					System.out.println("data at " + i + " , " +pixelData[i]);
			}
			this.data = pixelData;
			opened = true;
			return opened;
	}
	
	public void GetMinMax(double[] minmax){
		minmax[0] = 99999999;
		minmax[1] = 0;
		for(int i=0; i<this.size[0]*this.size[1]; i++){
			double value = this.getData()[i];
			if(value!=-1 && !Double.isNaN(value)){
				if(value<minmax[0])
					minmax[0] = this.getData()[i];
				if(value>minmax[1])
					minmax[1] = this.getData()[i];				
			}
		}		
	}
	
	public double[] getSize() {
		return size;
	}

	public void setSize(double[] size) {
		this.size = size;
	}
	public double[] getLrLatlng() {
		return lrLatlng;
	}
	public double[] getUlLatlng() {
		return ulLatlng;
	}
	public double[] getPixelHW() {
		return pixelHW;
	}
	public double[] getGeoUnitPerUW() {
		return geoUnitPerUW;
	}
	public double[] getStatistics() {
		return statistics;
	}
	public double[] getData() {
		return data;
	}
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
	public void close(){
		this.data = null;
	}
	
}
