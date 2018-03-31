package application;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.*;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.scene.layout.BorderPane;
import javafx.event.ActionEvent;
import javafx.fxml.*;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Scene;
import javafx.stage.Stage;

import utilities.Utilities;

import java.awt.image.BufferedImage;
import java.awt.Graphics;
import javax.imageio.ImageIO;

public class Controller {

	@FXML private ImageView imageView; // the image display window in the GUI
	@FXML private Slider slider;
	@FXML private Button imageButton;
	@FXML private Button playButton;
	@FXML private Button newWindowButton;

	private VideoCapture capture;
	private ScheduledExecutorService timer;
	private List<Mat> framesArray = new ArrayList<Mat>();
	private double[][] columnArray;


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
		return "resources/RedVideoWipeLower.mov";
	}


	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		// This method opens an image and display it using the GUI

		//this will reset framesArray when another video is loaded
		if(framesArray.size() != 0) {
			framesArray = new ArrayList<Mat>();
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
	protected void checkColours() {
		// This method "plays" the image opened by the user
		System.out.println("Warning, this will take a long time (10+ seconds)");

		//look at last slide of scanned files -> week11.pdf -> last page -> 2c ii) for information about project
		Mat frame = framesArray.get(0);
		int frameLength = framesArray.size();
		int frameCols = frame.cols();
		int frameRows = frame.rows();
		columnArray = new double[frameLength-1][frameCols];

		for(int i=0; i<frameLength-1; i++) {
			//gets first frame and the frame after
			Mat firstFrame = framesArray.get(i);
			Mat afterFrame = framesArray.get(i+1);

			//dimensions of video
			int rowSize = firstFrame.rows();
			int colSize = firstFrame.cols();

			//dimensions of histograms - Sturges' Rule
			int histogramDimensions = (int) (1.0 + Math.log(rowSize)/Math.log(2));

			//gets full pixel count
			for(int col=0; col<colSize; col++) {
				//get ready to make 2 new histograms
				double[][] firstHistogram = new double[histogramDimensions][histogramDimensions];
				double[][] secondHistogram = new double[histogramDimensions][histogramDimensions];

				for(int row=0; row<rowSize; row++) {
					//histogram for firstFrame or frame@(t)
					double[] rgbFirst = firstFrame.get(row, col);
					double[] chromaticityFirst = this.getChromaticity(rgbFirst); //get r and g chromaticity values
					int positionRedFirst = (int) Math.floor(chromaticityFirst[0] * histogramDimensions);
					int positionGreenFirst = (int) Math.floor(chromaticityFirst[1] * histogramDimensions);
				    firstHistogram[positionRedFirst][positionGreenFirst] += (1.0/rowSize);
					//firstHistogram[positionRedFirst][positionGreenFirst]++;

					//histogram for afterFrame or frame@(t+1)
					double[] rgbSecond = afterFrame.get(row, col);
					double[] chromaticitySecond = this.getChromaticity(rgbSecond); //get r and g chromaticity values
					int positionRedSecond = (int) Math.floor(chromaticitySecond[0] * histogramDimensions);
					int positionGreenSecond = (int) Math.floor(chromaticitySecond[1] * histogramDimensions);
					secondHistogram[positionRedSecond][positionGreenSecond] += (1.0/rowSize);
					//secondHistogram[positionRedSecond][positionGreenSecond]++;
				}
				this.compareHistograms(firstHistogram, secondHistogram, histogramDimensions, i, col);
			}
		}
		for(int m=0; m<frameLength-1; m=m+2) {
			for (int n=0; n<frameCols; n=n+2) {
				if (columnArray[m][n] >= 0.7) {
					System.out.print(1+" ");
				} else {
					System.out.print(0+" ");
				}
			}
			System.out.println("");
		}
	}

	private void compareHistograms(double[][] firstHistogram, double[][] secondHistogram, int histogramLength, int frame, int column) {
		//pixelTotal has to be used to divide, to normalize value
		double intersection = 0.0;
		for(int i=0; i<histogramLength; i++) {
			for(int j=0; j<histogramLength; j++) {
				if (firstHistogram[i][j] < secondHistogram[i][j]) {
					intersection += firstHistogram[i][j];
				} else {
					intersection += secondHistogram[i][j];
				}
			}
		}
		columnArray[frame][column] = intersection;
	}


	//self-explanatory value
	private double[] getChromaticity(double[] pixelRGB) {
		double[] chromaticity = new double[2];

		//pixelRGB indices follow RGB => B -> 0, G -> 1, R -> 2
		double newRGB = (pixelRGB[0]+pixelRGB[1]+pixelRGB[2]);
		double r, g;

		if (newRGB == 0) {
			r = 0;
			g = 0;
		} else {
			r = pixelRGB[2]/newRGB;
			g = pixelRGB[1]/newRGB;
		}

		//If colours are a pure red or green it causes an error.
		//These if statements just slightly reduce the chromacity so there isn't an error.
		if (r == 1) r = 0.9999;
		if (g == 1) g = 0.9999;

		chromaticity[0] = r;
		chromaticity[1] = g;

		return chromaticity; //returns array of 2 values for r, g
	}

	public void openWindow() {
		System.out.println("inside open function");


		//You just need to get this function to work. It has to produce a grey scale image of the double array
		//from the variable columnArray. Look up how to do it. I was trying to just get the image popped up with the code below


//		Mat frame = framesArray.get(0);
//		int frameLength = framesArray.size()-1;
//		int frameCols = frame.cols();
		//columnArray = new double[frameLength-1][frameCols];
		//dimensions of video
//		int height = frameLength;
//		int width = frameCols;
		double[][] newArray = new double[20][20];
		int height = 20;
		int width = 20;
		BufferedImage image = new BufferedImage(height, width, 3);


//		for (int x = 0; x < height; x++) {
//			for (int y = 0; y < width; y++) {
//				int rgb = (int)columnArray[x][y] << 16 | (int)columnArray[x][y] << 8 | (int)columnArray[x][y];
//				image.setRGB(x, y, rgb);
//			}
//		}

		for (int x = 0; x < height; x++) {
			for (int y = 0; y < width; y++) {
				int rgb = (int)newArray[x][y] << 16 | (int)newArray[x][y] << 8 | (int)newArray[x][y];
				image.setRGB(x, y, rgb);
			}
		}

		try {
			ImageIO.write(image, "Doublearray", new File("Doublearray.jpg"));
			System.out.println("out");
		} catch (IOException e) {
			System.out.println("could not open new file");
		}
//
//		try {
//			FXMLLoader loader = new FXMLLoader(getClass().getResource("GraphWindow.fxml"));
//			BorderPane root = (BorderPane)loader.load();
//
//			Scene scene = new Scene(root, 600, 400);
//			Stage stage = new Stage();
//
//
//			stage.setTitle("New Window");
//			stage.setScene(scene);
//			stage.show();
//		} catch (IOException e) {
//			Logger logger = Logger.getLogger(getClass().getName());
//			logger.log(Level.SEVERE, "Failed to create new Window.", e);
//		}
	}
}
