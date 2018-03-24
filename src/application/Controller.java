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
	@FXML private Button imageButton;
	@FXML private Button playButton;
	
	private VideoCapture capture;   
	private ScheduledExecutorService timer;
	private List<Mat> framesArray = new ArrayList<Mat>();
	private double[][] colourBuckets = new double[7][7];

	
	private String getImageFilename() {
//	    FileChooser fileName = new FileChooser();
//	    fileName.setTitle("Select Video:");
//	    File file = fileName.showOpenDialog(new Stage());
//
//	    if(file == null)
//	    	return null;
//
//	    return file.getAbsolutePath();

		//this defaults to the video in resources to make it easier
		return "resources/WipeVideoTestLower.mov";
	}
	
	
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		// This method opens an image and display it using the GUI

		//this will reset framesArray when another video is loaded
		if(framesArray.size() != 0) {
			framesArray = new ArrayList<Mat>();
			colourBuckets = new double[7][7];
		}
		
		 capture = new VideoCapture(getImageFilename()); // open video file  
		 if (capture.isOpened()) { // open successfully
			 createFrameGrabber(); //plays video
		 }
	}

	// this just plays the video
	protected void createFrameGrabber() throws InterruptedException {   
		if (capture != null && capture.isOpened()) { // the video must be open     
			double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
			
			// create a runnable to fetch new frames periodically

			Runnable frameGrabber = new Runnable() {
				@Override public void run() {
					Mat frame = new Mat();           
					if (capture.read(frame)) { // decode successfully
						Image im = Utilities.mat2Image(frame);
						//collects frames from entire video
						//video must run all the way through for all frames to be added to array
						framesArray.add(frame);

						Utilities.onFXThread(imageView.imageProperty(), im);              
						double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);             
						slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));     
					
					} else { // reach the end of the video             
							//capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
							capture.release();
							return;
						}
					}
				};

			//Not sure what this does but don't touch it

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
		System.out.println("Warning, this will take a long time (10+ seconds)");

		//look at last slide of scanned files-> week11.pdf -> last page -> 2c ii) for information about project

		int frameLength = framesArray.size();
		for(int i=0; i<frameLength-1; i++) {

			//gets first frame and the frame after
			Mat firstFrame = framesArray.get(i);
			Mat afterFrame = framesArray.get(i+1);

			//get ready to make 2 new histograms
			double[][] firstHistogram = new double[7][7];
			double[][] secondHistogram = new double[7][7];

			//dimensions of video
			int rowSize = firstFrame.rows();
			int colSize = firstFrame.cols();

			//gets full pixel count
			double pixelCount = (double) rowSize*colSize;

			for(int row=0; row<rowSize; row++) {
				for(int col=0; col<colSize; col++) {
					//histogram for firstFrame or frame@(t)
					double[] rgbFirst = firstFrame.get(row, col);
					double[] chromaticityFirst = this.getChromaticity(rgbFirst); //get r and g chromaticity values
					int positionRedFirst = (int) Math.floor(chromaticityFirst[0] * 6);
					int positionGreenFirst = (int) Math.floor(chromaticityFirst[1] * 6);
					firstHistogram[positionRedFirst][positionGreenFirst]++;

					//histogram for afterFrame or frame@(t+1)
					double[] rgbSecond = afterFrame.get(row, col);
					double[] chromaticitySecond = this.getChromaticity(rgbSecond); //get r and g chromaticity values
					int positionRedSecond = (int) Math.floor(chromaticitySecond[0] * 6);
					int positionGreenSecond = (int) Math.floor(chromaticitySecond[1] * 6);
					secondHistogram[positionRedSecond][positionGreenSecond]++;
				}
			}
			//finds min value between 2 histograms[i][j] and stores in colourBuckets array
			this.compareHistograms(firstHistogram, secondHistogram, pixelCount);
		}

		//just prints values. Can be commented out
		for(int i=0; i<7; i++) {
			for(int j=0; j<7; j++) {
				System.out.print(colourBuckets[i][j]+" ");
			}
			System.out.println("");
		}

		//make histogram
//		Imgproc.calcHist();
	}

	//self-explanatory value
	private double[] getChromaticity(double[] pixelRGB) {
		double[] chromaticity = new double[2];

		//pixelRGB indices follow RGB => 0 -> R, 1 -> G, 2 -> B
		double r = pixelRGB[0]/(pixelRGB[0]+pixelRGB[1]+pixelRGB[2]);
		double g = pixelRGB[1]/(pixelRGB[0]+pixelRGB[1]+pixelRGB[2]);
		chromaticity[0] = r;
		chromaticity[1] = g;

		return chromaticity; //returns array of 2 values for r, g
	}

	private void compareHistograms(double[][] firstHistogram, double[][] secondHistogram, double pixelTotal) {
		//pixelTotal has to be used to divide, to normalize value
		for(int i=0; i<7; i++) {
			for(int j=0; j<7; j++) {
				if (firstHistogram[i][j] < secondHistogram[i][j]) {
					colourBuckets[i][j] = (firstHistogram[i][j]/pixelTotal);
				} else {
					colourBuckets[i][j] = (secondHistogram[i][j]/pixelTotal);
				}

			}
		}
	}
}
