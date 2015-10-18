import java.util.ArrayList;


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
	
	
}
