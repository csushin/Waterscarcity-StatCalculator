public class LoadTiffThread implements Runnable{
	private TiffParser parser;
	private String filePath;
	
	public LoadTiffThread(String filePath){
		this.filePath = filePath;
	}
	
	public TiffParser getResult(){
		return this.parser;
	}
	
	public void run(){
//		System.out.println("Start parsing file: " + this.filePath);
		this.parser = new TiffParser(this.filePath);
	}
}

