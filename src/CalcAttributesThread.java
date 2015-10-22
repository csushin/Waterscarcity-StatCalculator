import java.util.ArrayList;
import java.util.Collections;


public class CalcAttributesThread implements Runnable {
	private String type;
	private int startIndex;
	private int endIndex;
	private double[] results;
	private ArrayList<TiffParser> parsers;
	
	public CalcAttributesThread(String type, int startIndex, int endIndex, ArrayList<TiffParser> parsers, double[] bufferSet){
		this.type = type;
		this.startIndex = startIndex;
		this.endIndex = endIndex;
		this.parsers = parsers;
		this.results = bufferSet;
	}
	
	public double[] getResults(){
		return this.results;
	}
	
	public void run(){
		for(int i=startIndex; i<endIndex; i++){
			double[] scarcities = new double[parsers.size()];
			for(int k=0; k<this.parsers.size(); k++){
				scarcities[k] = this.parsers.get(k).getData()[i];
			}
			if(type.equals("ModalMean") || type.equals("TimeMean")){
				this.results[i] = computeTimeOrModalMean(scarcities);
			}
			if(type.equals("ModalStd") || type.equals("TimeStd")){
				this.results[i] = computeTimeOrModalStd(scarcities);
			}
			if(type.equals("ModalEntropy") || type.equals("TimeEntropy")){
				this.results[i] = computeTimeOrModalEntropy(scarcities);
			}
			if(type.equals("ModalCV") || type.equals("TimeCV")){
				this.results[i] = computeTimeOrModalCV(scarcities);
			}
			if(type.equals("ModalRMS") || type.equals("TimeRMS")){
				this.results[i] = computeTimeOrModalRMS(scarcities);
			}
		}
	}
	
	private double computeTimeOrModalMean(double[] scarcities){
		double total = 0;
		double totalnum = 0;
		for(double val : scarcities){
			if(val == -1)
				continue;
			totalnum++;
			total+=val;
		}
		if(totalnum == 0)
			return -1;
		else 
			return total/totalnum;
	}
	
	private double computeTimeOrModalStd(double[] scarcities){
		double mean = computeTimeOrModalMean(scarcities);
		if(mean == -1)
			return -1;
		else{
			double sqrsum = 0;
			double nonneg = 0;
			for(int i=0; i<scarcities.length; i++){
				if(scarcities[i] != -1){
					nonneg++;
					sqrsum+= Math.pow(scarcities[i]-mean, 2.0);
				}
			}
			if(nonneg == 0)
				return -1;
			else
				return Math.sqrt(sqrsum/nonneg);	
		}
	}
	
	private double computeTimeOrModalEntropy(double[] scarcities){
		int type = 4;
		double[] occurences = new double[type];
		double entropy = 0;
		int nonNeg=0;
		int totalType = 0;
		for(double value : scarcities){
			if(value==-1)
				continue;
			int level = (int) categorizeScarcity(value);
			occurences[level-1]++;
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
			entropy = 0;
		return entropy;
//		Normalize the entropy, referred to http://www.endmemo.com/bio/shannonentropy.php
//		double MaxEntropy = Math.log(scarcities.length)/Math.log(nenNeg);
//		return entropy/(MaxEntropy-0);
	}
	
	private double computeTimeOrModalCV(double[] scarcities){
		double mean = computeTimeOrModalMean(scarcities);
		if(mean == -1)
			return -1;
		double std = computeTimeOrModalStd(scarcities);
		if(std == -1)
			return -1;
		return std/mean;
	}
	
	private double computeTimeOrModalRMS(double[] scarcities){
		double sqrsum = 0;
		double nonneg = 0;
		for(int i=0; i<scarcities.length; i++){
			if(scarcities[i] != -1){
				nonneg++;
				sqrsum+= Math.pow(scarcities[i], 2.0);
			}
		}
		if(nonneg == 0)
			return -1;
		else
			return Math.sqrt(sqrsum/nonneg);	
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
