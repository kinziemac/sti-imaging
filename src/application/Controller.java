package application;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.*;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import javafx.event.ActionEvent;
import javafx.fxml.*;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

import utilities.Utilities;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;


public class Controller {

	@FXML private ImageView imageView;
	@FXML private Slider slider;

	private VideoCapture capture;
	private ScheduledExecutorService timer;
	private List<Mat> framesArray = new ArrayList<Mat>();
	private double[][] columnArray;


	private String getImageFilename() {
	    FileChooser fileName = new FileChooser();
	    fileName.setTitle("Select Video:");
	    File file = fileName.showOpenDialog(new Stage());

	    if(file == null)
	    	return null;

	    return file.getAbsolutePath();
	}


	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {

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
		System.out.println("Warning, this will take a long time (10+ seconds)");

		Mat frame = framesArray.get(0);
		int frameLength = framesArray.size();
		int frameCols = frame.cols();
		columnArray = new double[frameLength-1][frameCols];

		for(int i=0; i<frameLength-1; i++) {
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
					double[] chromaticityFirst = this.getChromaticity(rgbFirst);
					int positionRedFirst = (int) Math.floor(chromaticityFirst[0] * histogramDimensions);
					int positionGreenFirst = (int) Math.floor(chromaticityFirst[1] * histogramDimensions);
				    firstHistogram[positionRedFirst][positionGreenFirst] += (1.0/rowSize);

					//histogram for afterFrame or frame@(t+1)
					double[] rgbSecond = afterFrame.get(row, col);
					double[] chromaticitySecond = this.getChromaticity(rgbSecond);
					int positionRedSecond = (int) Math.floor(chromaticitySecond[0] * histogramDimensions);
					int positionGreenSecond = (int) Math.floor(chromaticitySecond[1] * histogramDimensions);
					secondHistogram[positionRedSecond][positionGreenSecond] += (1.0/rowSize);
				}
				this.compareHistograms(firstHistogram, secondHistogram, histogramDimensions, i, col);
			}
		}
		this.createImage();
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

		return chromaticity;
	}

	public void createImage() {
		Mat frame = framesArray.get(0);
		int frameLength = framesArray.size()-1;
		int frameCols = frame.cols();
//		dimensions of video
		int height = frameLength;
		int width = frameCols;

		BufferedImage newImage = new BufferedImage(height, width, BufferedImage.TYPE_BYTE_GRAY);


		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				int value = 255;
				//0.7 is the threshold
				if (columnArray[i][j] < 0.7) value = 0;

				int rgb = value << 16 | value << 8 | value;
				int xCoordinate = i;
				int yCoordinate = (width-1) - j;

				newImage.setRGB(xCoordinate, yCoordinate, rgb);
			}
		}

		try {
			ImageIO.write(newImage, "png", new File("./grayscaleImage.png"));
			System.out.println("created grayscaleImage.png");
		} catch (IOException e) {
			System.out.println("could not open new file");
		}
	}
}
