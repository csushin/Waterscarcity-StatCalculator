import java.util.ArrayList;
import java.util.Arrays;


public class CalcStatWithoutThread implements Runnable{
	private int startIndex;
	private int endIndex;
	private double[] results;
	private double[] values;
	private String dataType;
	private double[] minmax;
	private String metricType;
	private double finalValue;
	private int binSize = 20;
	
	@Override
	public void run() {
		if(this.metricType.contains("Mean"))
			this.finalValue = this.computeTimeOrModalMean(this.values);
		else if(this.metricType.contains("Std"))
			this.finalValue = this.computeTimeOrModalStd(this.values);
		else if(this.metricType.contains("CV"))
			this.finalValue = this.computeTimeOrModalCV(this.values);
		else if(this.metricType.contains("IQR"))
			this.finalValue = this.computeTimeOrModalIQR(this.values);
		else if(this.metricType.contains("Skewness"))
			this.finalValue = this.computeTimeOrModalSkewness(this.values);
		else if(this.metricType.contains("Kurtosis"))
			this.finalValue = this.computeTimeOrModalKurtosis(this.values);
		else if(this.metricType.contains("Entropy")){
			if(this.dataType.contains("Scarcity"))
				this.finalValue = this.computeTimeOrModalCategoricalEntropy(this.values);
			else
				this.finalValue = this.computeTimeOrModalBinEntropy(this.values, this.binSize);
		}
		else if(this.metricType.contains("QuadraticScore")){
			if(this.dataType.contains("Scarcity"))
				this.finalValue = this.computeTimeOrModalQuadraticScore_Discrete(this.values);
			else
				this.finalValue = this.computeTimeOrModalQuadraticScore_Continuous(this.values, this.binSize);
		}
			
	}
	
	public double getResult(){
		return this.finalValue;
	}
	
	public CalcStatWithoutThread(String dataType, String metricType, double[] minmax, double[] values){
		this.dataType = dataType;
		this.minmax = minmax;
		this.metricType = metricType;
		this.values = values;
	}
	
	public double computeTimeOrModalMean(double[] values){
		double total = 0;
		double totalnum = 0;
//		if(!dataType.contains("Skewness")){
			for(double val : values){
				if(Double.isNaN(val) && val!=-1)
					continue;
				totalnum++;
				total+=val;
			}
			if(totalnum == 0)
				return Double.NaN;
			else 
				return total/totalnum;			
//		}
//		else{
//			return Double.NaN;
//		}
	}
	
	public double computeTimeOrModalStd(double[] values){
		double mean = computeTimeOrModalMean(values);
		if(mean == -1 || mean ==0 || Double.isNaN(mean))
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
	
	public double computeTimeOrModalBinEntropy(double[] values, int binSize){
		double entropy = 0;
		ArrayList<Double> newvalues = new ArrayList<Double>();
		for(int i=0; i<values.length; i++){
			if(values[i] == -1 || Double.isNaN(values[i])){
				continue;
			}
			newvalues.add(values[i]);
		}
		Double[] newvaluesDouble = new Double[newvalues.size()];
		newvalues.toArray(newvaluesDouble);
		Arrays.sort(newvaluesDouble);
		double min = this.minmax[0];
		double max = this.minmax[1];
		double nonNaN = 0;
		for(double value : newvaluesDouble){
			if(!Double.isNaN(value) && value!=-1){
				nonNaN++;
			}
		}
		if(nonNaN == 0)
			return Double.NaN;
		double interval = (max - min + 1)/binSize;
		double[] binArrays = new double[(int) binSize];
		for(double value : newvaluesDouble){
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
	
	public double computeTimeOrModalCategoricalEntropy(double[] values){
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
	
	public double computeTimeOrModalCV(double[] values){
		double mean = computeTimeOrModalMean(values);
		if(mean == -1 || mean == 0 || Double.isNaN(mean))
			return Double.NaN;
		double std = computeTimeOrModalStd(values);
		if(std == -1|| Double.isNaN(std))
			return Double.NaN;
		return std/mean;
	}
	
	public double computeTimeOrModalRMS(double[] values){
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
	public double categorizeScarcity(double scarcity){
		if(scarcity<=500 && scarcity>=0) {return 1;}
		else if(scarcity>500 && scarcity<=1000) {return 2;}
		else if(scarcity>1000 && scarcity<=1700) {return 3;}
		else if(scarcity>1700)	{return 4;}
		else return Double.NaN;
	}
	
	public double computeTimeOrModalKurtosis(double[] values){
		double mean = computeTimeOrModalMean(values);
		if(mean == -1 || Double.isNaN(mean))
			return Double.NaN;
		double std = computeTimeOrModalStd(values);
		if(std == -1|| Double.isNaN(std))
			return Double.NaN;
		double result = 0;
		for(double each : values){
			if(each == -1 || Double.isNaN(each))
				continue;
			result+=Math.pow(each-mean, 4);
		}
		return result/(values.length*Math.pow(std, 4));
	}
	
	public double computeTimeOrModalSkewness(double[] values){
		double mean = computeTimeOrModalMean(values);
		if(mean == -1 || Double.isNaN(mean))
			return Double.NaN;
		double std = computeTimeOrModalStd(values);
		if(std == -1|| Double.isNaN(std))
			return Double.NaN;
		double result = 0;
		for(double each : values){
			if(each == -1 || Double.isNaN(each))
				continue;
			result+=Math.pow(each-mean, 3);
		}
		return result/(values.length*Math.pow(std, 3));
	}
	
	public double computeTimeOrModalMedian(double[] values){
		ArrayList<Double> newvalues = new ArrayList<Double>();
		for(int i=0; i<values.length; i++){
			if(values[i] == -1 || Double.isNaN(values[i])){
				continue;
			}
			newvalues.add(values[i]);
		}
		Double[] newvaluesDouble = new Double[newvalues.size()];
		newvalues.toArray(newvaluesDouble);
		Arrays.sort(newvaluesDouble);
		if(newvaluesDouble.length%2==0)
			return (newvaluesDouble[newvaluesDouble.length/2-1]+newvaluesDouble[newvaluesDouble.length/2])/2.0;
		else
			return newvaluesDouble[(newvaluesDouble.length-1)/2];
	}

	public double computeTimeOrModalIQR(double[] values){
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
	
	public double computeTimeOrModalQuadraticScore_Continuous(double[] values, int binSize){
		double weighted = 0;
		double min = this.minmax[0];
		double max = this.minmax[1];
		double nonNaN = 0;
		ArrayList<Double> newvalues = new ArrayList<Double>();
		for(int i=0; i<values.length; i++){
			if(values[i] == -1 || Double.isNaN(values[i])){
				continue;
			}
			newvalues.add(values[i]);
		}
		Double[] newvaluesDouble = new Double[newvalues.size()];
		newvalues.toArray(newvaluesDouble);
		Arrays.sort(newvaluesDouble);
		for(double value : newvaluesDouble){
			if(!Double.isNaN(value) && value!=-1){
				nonNaN++;
			}
		}
		if(nonNaN == 0)
			return Double.NaN;
		double[] binArrays = new double[(int) binSize];
		for(double value : newvaluesDouble){
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
	
	public double computeTimeOrModalQuadraticScore_Discrete(double[] values){
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
