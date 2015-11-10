import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;


public class CalcAttributesThread implements Runnable {
	private String type;
	private int startIndex;
	private int endIndex;
	private double[] results;
	private ArrayList<TiffParser> parsers;
	private String dataType;
	private int binSize = 20;
	private double[] minmax;
	
	public CalcAttributesThread(String dataType, String type, int startIndex, int endIndex, ArrayList<TiffParser> parsers, double[] bufferSet, double[] minmax){
		this.type = type;
		this.startIndex = startIndex;
		this.endIndex = endIndex;
		this.parsers = parsers;
		this.results = bufferSet;
		this.dataType = dataType;
		this.minmax = minmax;
	}
	
	public double[] getResults(){
		return this.results;
	}
	
	public void run(){
		for(int i=startIndex; i<endIndex; i++){
			double[] scarcities = new double[parsers.size()];
			for(int k=0; k<this.parsers.size(); k++){
				scarcities[k] = this.parsers.get(k).getData()[i];
				if(dataType.equals("Scarcity"))
					scarcities[k] = categorizeScarcity(scarcities[k]);
			}
			if(type.equals("ModalMean") || type.equals("TimeMean") || type.equals("EnsembleMean")){
				this.results[i] = computeTimeOrModalMean(scarcities);
			}
			if(type.equals("ModalStd") || type.equals("TimeStd") || type.equals("EnsembleStd")){
				this.results[i] = computeTimeOrModalStd(scarcities);
			}
			if(type.equals("ModalEntropy") || type.equals("TimeEntropy") || type.equals("EnsembleEntropy")){
				if(!this.dataType.equals("Scarcity"))
					this.results[i] = computeTimeOrModalBinEntropy(scarcities, this.binSize);
				else
					this.results[i] = computeTimeOrModalCategoricalEntropy(scarcities);
			}
			if(type.equals("ModalCV") || type.equals("TimeCV") || type.equals("EnsembleCV")){
				this.results[i] = computeTimeOrModalCV(scarcities);
			}
			if(type.equals("ModalRMS") || type.equals("TimeRMS") || type.equals("EnsembleRMS")){
				this.results[i] = computeTimeOrModalRMS(scarcities);
			}
			if(type.equals("ModalMedian") || type.equals("TimeMedian") || type.equals("EnsembleMedian")){
				this.results[i] = computeTimeOrModalMedian(scarcities);
			}
			if(type.equals("ModalIQR") || type.equals("TimeIQR") || type.equals("EnsembleIQR")){
				this.results[i] = computeTimeOrModalIQR(scarcities);
			}
			if(type.equals("ModalSpaceGradient") || type.equals("TimeSpaceGradient") || type.equals("EnsembleSpaceGradient")){
				
			}
			if(type.equals("ModalSkewness") || type.equals("TimeSkewness") || type.equals("EnsembleSkewness")){
				this.results[i] = computeTimeOrModalSkewness(scarcities);
			}
			if(type.equals("ModalKurtosis") || type.equals("TimeKurtosis") || type.equals("EnsembleKurtosis")){
				this.results[i] = computeTimeOrModalKurtosis(scarcities);
			}
			if(type.equals("ModalQuadraticScore") || type.equals("TimeQuadraticScore") || type.equals("EnsembleQuadraticScore")){
				if(!this.dataType.equals("Scarcity"))
					this.results[i] = computeTimeOrModalQuadraticScore_Continuous(scarcities, this.binSize);
				else
					this.results[i] = computeTimeOrModalQuadraticScore_Discrete(scarcities);
			}
		}
	}
	
	private double computeTimeOrModalMean(double[] values){
		double total = 0;
		double totalnum = 0;
		if(!dataType.contains("Skewness")){
			for(double val : values){
				if(Double.isNaN(val) && val!=-1)
					return Double.NaN;
				totalnum++;
				total+=val;
			}
			if(totalnum == 0)
				return Double.NaN;
			else 
				return total/totalnum;			
		}
		else{
			return Double.NaN;
		}
	}
	
	private double computeTimeOrModalStd(double[] values){
		double mean = computeTimeOrModalMean(values);
		if(mean == -1 || Double.isNaN(mean))
			return Double.NaN;
		else{
			double sqrsum = 0;
			double nonneg = 0;
			for(int i=0; i<values.length; i++){
				if(values[i] != -1 && !Double.isNaN(values[i])){
					nonneg++;
					sqrsum+= Math.pow(values[i]-mean, 2.0);
				}
			}
			if(nonneg == 0)
				return Double.NaN;
			else
				return Math.sqrt(sqrsum/nonneg);	
		}
	}
	
	private double computeTimeOrModalBinEntropy(double[] values, int binSize){
		double entropy = 0;
		Arrays.sort(values);
		double min = this.minmax[0];
		double max = this.minmax[1];
		double nonNaN = 0;
		for(double value : values){
			if(!Double.isNaN(value) && value!=-1){
				nonNaN++;
			}
		}
		if(nonNaN == 0)
			return Double.NaN;
		double interval = (max - min + 1)/binSize;
		double[] binArrays = new double[(int) binSize];
		for(double value : values){
			if(!Double.isNaN(value) && value!=-1){
				int index = (int) ((value - min)/interval);
				binArrays[index]++;
			}
		}
		double nonZeroBin = 0;
		for(double each : binArrays){
			double pr = each / nonNaN;
			if(pr!=0){
				entropy+=pr*Math.log(pr);
				nonZeroBin++;
			}
		}
		return -entropy/Math.log(nonZeroBin);
		
	}
	
	private double computeTimeOrModalCategoricalEntropy(double[] values){
		int type = 4;
		double[] occurences = new double[type];
		double entropy = 0;
		int nonNeg=0;
		int totalType = 0;
		for(double value : values){
			if(value == -1  || Double.isNaN(value))
				continue;
//			int level = (int) categorizeScarcity(value);
			occurences[(int) (value-1)]++;
			nonNeg++;
		}
		if(nonNeg>0){
			for(int i=0; i<type; i++){
				if(occurences[i]>0){
					occurences[i]=occurences[i]/(double)nonNeg;
					entropy+=(occurences[i]*Math.log(occurences[i]));
					totalType++;
				}
			}
			entropy = -entropy;
//			entropy = -entropy/Math.log(totalType);
		}
		else
			entropy = Double.NaN;
		return entropy;
//		Normalize the entropy, referred to http://www.endmemo.com/bio/shannonentropy.php
//		double MaxEntropy = Math.log(scarcities.length)/Math.log(nenNeg);
//		return entropy/(MaxEntropy-0);
	}
	
	private double computeTimeOrModalCV(double[] values){
		double mean = computeTimeOrModalMean(values);
		if(mean == -1 || mean == 0 || Double.isNaN(mean))
			return Double.NaN;
		double std = computeTimeOrModalStd(values);
		if(std == -1|| Double.isNaN(std))
			return Double.NaN;
		return std/mean;
	}
	
	private double computeTimeOrModalRMS(double[] values){
		double sqrsum = 0;
		double nonneg = 0;
		for(int i=0; i<values.length; i++){
			if(values[i] != -1 && !Double.isNaN(values[i])){
				nonneg++;
				sqrsum+= Math.pow(values[i], 2.0);
			}
		}
		if(nonneg == 0)
			return Double.NaN;
		else
			return Math.sqrt(sqrsum/nonneg);	
	}
	
	
//	output continuous scarcity value to categories from 1~4
	private double categorizeScarcity(double scarcity){
		if(scarcity<=500 && scarcity>=0) {return 1;}
		else if(scarcity>500 && scarcity<=1000) {return 2;}
		else if(scarcity>1000 && scarcity<=1700) {return 3;}
		else if(scarcity>1700)	{return 4;}
		else return Double.NaN;
	}
	
	private double computeTimeOrModalKurtosis(double[] values){
		double mean = computeTimeOrModalMean(values);
		if(mean == -1 || Double.isNaN(mean))
			return Double.NaN;
		double std = computeTimeOrModalStd(values);
		if(std == -1|| Double.isNaN(std))
			return -Double.NaN;
		double result = 0;
		for(double each : values){
			if(each == -1 || Double.isNaN(each))
				return Double.NaN;
			result+=Math.pow(each-mean, 4);
		}
		return result/(values.length*Math.pow(std, 4));
	}
	
	private double computeTimeOrModalSkewness(double[] values){
		double mean = computeTimeOrModalMean(values);
		if(mean == -1 || Double.isNaN(mean))
			return Double.NaN;
		double std = computeTimeOrModalStd(values);
		if(std == -1|| Double.isNaN(std))
			return Double.NaN;
		double result = 0;
		for(double each : values){
			if(each == -1 || Double.isNaN(each))
				return Double.NaN;
			result+=Math.pow(each-mean, 3);
		}
		return result/(values.length*Math.pow(std, 3));
	}
	
	private double computeTimeOrModalMedian(double[] values){
		for(int i=0; i<values.length; i++){
			if(values[i] == -1 || Double.isNaN(values[i])){
				return Double.NaN;
			}
		}
		Arrays.sort(values);
		if(values.length%2==0)
			return (values[values.length/2-1]+values[values.length/2])/2.0;
		else
			return values[(values.length-1)/2];
	}

	private double computeTimeOrModalIQR(double[] values){
		computeTimeOrModalMedian(values);
		double[] prevSet = Arrays.copyOfRange(values, 0, values.length/2+1);
		double prevMed = computeTimeOrModalMedian(prevSet);
		double[] afterSet = Arrays.copyOfRange(values, values.length/2, values.length-1);
		double afterMed = computeTimeOrModalMedian(afterSet);
		if(prevMed == -1 || afterMed == -1 || Double.isNaN(prevMed) || Double.isNaN(afterMed))
			return Double.NaN;
		else
			return afterMed - prevMed;
	}
	
	private double computeTimeOrModalQuadraticScore_Continuous(double[] values, int binSize){
		double weighted = 0;
		Arrays.sort(values);
		double min = this.minmax[0];
		double max = this.minmax[1];
		double nonNaN = 0;
		for(double value : values){
			if(!Double.isNaN(value) && value!=-1){
				nonNaN++;
			}
		}
		if(nonNaN == 0)
			return Double.NaN;
		double[] binArrays = new double[(int) binSize];
		for(double value : values){
			if(!Double.isNaN(value) && value!=-1){
				int index = (int) ((value - min)/(max - min + 1) * binSize);
				binArrays[index]++;
			}
		}
		for(double each : binArrays){
			double pr = each / nonNaN;
			if(pr!=0){
				weighted+=pr*(1.0-pr);
			}
		}
		return weighted;
	}
	
	private double computeTimeOrModalQuadraticScore_Discrete(double[] values){
		int type = 4;
		double[] occurences = new double[type];
		double weighted = 0;
		int nonNeg=0;
		int totalType = 0;
		for(double value : values){
			if(value==-1  || Double.isNaN(value))
				continue;
//			int level = (int) categorizeScarcity(value);
			occurences[(int) (value-1)]++;
			nonNeg++;
		}
		if(nonNeg>0){
			for(int i=0; i<type; i++){
				if(occurences[i]>0){
					occurences[i]=occurences[i]/(double)nonNeg;
					weighted+=occurences[i]*(1.0-occurences[i]);
					totalType++;
				}
			}
		}
		else
			weighted = Double.NaN;
		return weighted;
	}

	
}
