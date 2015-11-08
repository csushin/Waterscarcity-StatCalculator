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
			if(type.equals("ModalRMS") ||  type.equals("TimeRMS")){
				this.results[i] = computeTimeOrModalRMS(scarcities);
			}
			if(type.equals("ModalCV") || type.equals("ModalCV")){
				this.results[i] = computeTimeOrModalCV(scarcities);
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
					sqrsum += Math.pow(scarcities[i]-mean, 2.0);
				}
			}
			if(nonneg == 0)
				return -1;
			else
				return Math.sqrt(sqrsum/nonneg);	
		}
	}
	
	private double computeTimeOrModalRMS(double[] scarcities){
		double mean = computeTimeOrModalMean(scarcities);
		if(mean == -1)
			return -1;
		else{
			double sqrsum = 0;
			double nonneg = 0;
			for(int i=0; i<scarcities.length; i++){
				if(scarcities[i] != -1){
					nonneg++;
					sqrsum += Math.pow(scarcities[i], 2.0);
				}
			}
			if(nonneg == 0)
				return -1;
			else
				return Math.sqrt(sqrsum/nonneg);	
		}
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
	
	private double computeTimeOrModalEntropy(double[] scarcities){
		int type = 4;
		double[] occurences = new double[type];
		double entropy = 0;
		double nonzero = 0;
		for(double val : scarcities){
			if(val>1700)
				occurences[3]++;
			if(val<=1700 && val>1000)
				occurences[2]++;
			if(val<=1000 && val>500)
				occurences[1]++;
			if(val<=500 && val>0)
				occurences[0]++;
		}
		occurences[0] = occurences[0]/scarcities.length;
		occurences[1] = occurences[1]/scarcities.length;
		occurences[2] = occurences[2]/scarcities.length;
		occurences[3] = occurences[3]/scarcities.length;
		for(int i=0; i<occurences.length; i++){
			if(occurences[i]!=0){
				entropy+=occurences[i]*Math.log(occurences[i]);
				nonzero++;
			}
		}
		entropy = entropy/Math.log(nonzero);
		double MaxEntropy = Math.log(scarcities.length)/Math.log(nonzero);
		return entropy/(MaxEntropy-0);
	}
	
}
