package application;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.*;
import utilities.Utilities;

public class Controller {
	
	@FXML private ImageView imageView; // the image display window in the GUI
	@FXML private Slider slider;
	@FXML private Button openButton;
	@FXML private Button playButton;
	
	private VideoCapture capture;   
	private ScheduledExecutorService timer;
	private Mat image;
	private boolean isEven = false;
	private List<Mat> framesArray = new ArrayList<Mat>();
	private int width;
	private int height;
	private int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	
	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
		width = 64;
		height = 64;
		sampleRate = 8000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;
		
		numberOfQuantizionLevels = 16;
		
		numberOfSamplesPerColumn = 125;
		
		// assign frequencies for each particular row
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}
	}
	
	private String getImageFilename() {
		// This method should return the filename of the image to be played
		// You should insert your code here to allow user to select the file
	    FileChooser fileName = new FileChooser();
	    fileName.setTitle("Select Video:");
	    File file = fileName.showOpenDialog(new Stage());

	    if(file == null)
	    	return null;

	    return file.getAbsolutePath();
		
		//return "resources/test.mp4";
	}
	
	
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		// This method opens an image and display it using the GUI
		// You should modify the logic so that it opens and displays a video
		
		 capture = new VideoCapture(getImageFilename()); // open video file  
		 if (capture.isOpened()) { // open successfully   
			 
			 createFrameGrabber();
			 
		 }
		 
		// You don't have to understand how mat2Image() works. 
		// In short, it converts the image from the Mat format to the Image format
		// The Mat format is used by the opencv library, and the Image format is used by JavaFX
		// BTW, you should be able to explain briefly what opencv and JavaFX are after finishing this assignment
	}
	
	protected void createFrameGrabber() throws InterruptedException {   
		if (capture != null && capture.isOpened()) { // the video must be open     
			double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
			
			// create a runnable to fetch new frames periodically     
			Runnable frameGrabber = new Runnable() {    

				@Override public void run() {
					Mat frame = new Mat();           
					if (capture.read(frame)) { // decode successfully
						Image im = Utilities.mat2Image(frame); 
						double frameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						if(frameNumber%30==0) {
							framesArray.add(frame);
						}
						image = frame;
						Utilities.onFXThread(imageView.imageProperty(), im);              
						double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);             
						slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));     
					
					} else { // reach the end of the video             
							//capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
							
							capture.release();
						} 
					}
				};
			
      // terminate the timer if it is running      
    if (timer != null && !timer.isShutdown()) {       
    	timer.shutdown();       
    	timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);     
    	}        
    // run the frame grabber    
    timer = Executors.newSingleThreadScheduledExecutor();     
    timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS); 
	} 

	};
	
	protected void playBeep() throws LineUnavailableException {
		AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
        SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
        sourceDataLine.open(audioFormat, sampleRate);
        sourceDataLine.start();
        
        byte[] beepNoise = new byte[3];
        beepNoise[0] = (byte) 900;
        beepNoise[1] = (byte) 900;
        beepNoise[2] = (byte) 900;
		sourceDataLine.write(beepNoise, 0, 3);
		
		sourceDataLine.drain();
        sourceDataLine.close();
	}
	
	
	
	@FXML
	protected void playImage(ActionEvent event) throws LineUnavailableException {
		// This method "plays" the image opened by the user
		// You should modify the logic so that it plays a video rather than an image
		
		int arraySize = framesArray.size();
		for(int index=0;index<arraySize;index++) {
			image = framesArray.get(index);
			
			if (image != null) {
				// convert the image from RGB to grayscale
				Mat grayImage = new Mat();
				Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
				
				// resize the image
				Mat resizedImage = new Mat();
				Imgproc.resize(grayImage, resizedImage, new Size(width, height));
				
				// quantization
				double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
				for (int row = 0; row < resizedImage.rows(); row++) {
					for (int col = 0; col < resizedImage.cols(); col++) {
						roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
					}
				}
				
				// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
		        AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
	            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
	            sourceDataLine.open(audioFormat, sampleRate);
	            sourceDataLine.start();
	          
	            
	            //this is just for one frame
	            for (int col = 0; col < width; col++) {
	            	byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
	            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
	            		double signal = 0;
	                	for (int row = 0; row < height; row++) {
	                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
	                		int time = t + col * numberOfSamplesPerColumn;
	                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
	                		signal += roundedImage[row][col] * ss;
	                	}
	                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
	                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
	            	}
	
	            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
	            }
	            
	            //Beep every 2 frames
	            if (isEven) {
	                 playBeep();
	                 isEven = false;
	            } else {
	            	isEven = true;
	            }
	           
	            sourceDataLine.drain();
	            sourceDataLine.close();
			}
		}
		
	} 
}
