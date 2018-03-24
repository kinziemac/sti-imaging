package application;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.*;

import javax.sound.sampled.LineUnavailableException;

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
	private List<Mat> framesArray = new ArrayList<Mat>();
	private double[][] colourBuckets = new double[7][7];

	
	private String getImageFilename() {
		// This method should return the filename of the image to be played
		// You should insert your code here to allow user to select the file
//	    FileChooser fileName = new FileChooser();
//	    fileName.setTitle("Select Video:");
//	    File file = fileName.showOpenDialog(new Stage());
//
//	    if(file == null)
//	    	return null;
//
//	    return file.getAbsolutePath();
		
		return "resources/WipeVideoTestLower.mov";
	}
	
	
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		// This method opens an image and display it using the GUI
		// You should modify the logic so that it opens and displays a video
		
		 capture = new VideoCapture(getImageFilename()); // open video file  
		 if (capture.isOpened()) { // open successfully
			 createFrameGrabber();
		 }
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
						framesArray.add(frame);

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


	@FXML
	protected void checkColours(ActionEvent event) throws LineUnavailableException {
		// This method "plays" the image opened by the user
		System.out.println("Warning, this will take a few seconds to load");

		//look at last slide of scanned files-> week11.pdf -> last page -> 2c ii)
		int frameLength = framesArray.size();
		for(int i=0; i<frameLength; i++) {
			Mat frame = framesArray.get(i);
			int rowSize = frame.rows();
			int colSize = frame.cols();
			for(int row=0; row<rowSize; row++) {
				for(int col=0; col<colSize; col++) {
					double[] rgb = frame.get(row, col);
					double[] chromaticity = this.getChromaticity(rgb);

					//making data for graph in the 7x7 matrix
					int positionR = (int) Math.floor(chromaticity[0] * 6);
					int positionG = (int) Math.floor(chromaticity[1] * 6);
					colourBuckets[positionR][positionG]++;
				}
			}
		}

		for(int i=0; i<7; i++) {
			for(int j=0; j<7; j++) {
				System.out.print(colourBuckets[i][j]+" ");
			}
			System.out.println("");
		}
	}

	//self-explanatory value
	private double[] getChromaticity(double[] pixelRGB) {
		double[] chromaticity = new double[2];
		double r = pixelRGB[0]/(pixelRGB[0]+pixelRGB[1]+pixelRGB[2]);
		double g = pixelRGB[1]/(pixelRGB[0]+pixelRGB[1]+pixelRGB[2]);
		chromaticity[0] = r;
		chromaticity[1] = g;

		return chromaticity;

	}
}
