package application;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.*;

import javax.sound.sampled.LineUnavailableException;

import javafx.scene.layout.BorderPane;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.Event;
import javafx.fxml.*;
import java.io.IOException;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.chart.BarChart;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.Scene;
import javafx.stage.*;
import javafx.stage.Stage;
import utilities.Utilities;

public class Controller {
	
	@FXML private ImageView imageView; // the image display window in the GUI
	@FXML private Slider slider;
	@FXML private Button imageButton;
	@FXML private Button playButton;
	@FXML private Button newWindowButton;
	
	private VideoCapture capture;   
	private ScheduledExecutorService timer;
	private List<Mat> framesArray = new ArrayList<Mat>();
	private List<Double> colourBuckets = new ArrayList<Double>();
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
			colourBuckets = new ArrayList<Double>();
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
		for(int m=0; m<frameLength-1; m++) {
			for (int n=0; n<frameCols; n++) {
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
		try {
//			FXMLLoader fxmlLoader = new FXMLLoader();
//
//			fxmlLoader.setLocation(getClass().getResource("GraphWindow.fxml"));
			FXMLLoader loader = new FXMLLoader(getClass().getResource("GraphWindow.fxml"));
			BorderPane root = (BorderPane)loader.load();

			Scene scene = new Scene(root, 600, 400);
			Stage stage = new Stage();

			stage.setTitle("Bar Chart Sample");
			final NumberAxis xAxis = new NumberAxis();
			final CategoryAxis yAxis = new CategoryAxis();
			final BarChart<Number,String> bc =
					new BarChart<Number,String>(xAxis,yAxis);
			bc.setTitle("Country Summary");
			xAxis.setLabel("Value");
			xAxis.setTickLabelRotation(90);
			yAxis.setLabel("Country");

			XYChart.Series series1 = new XYChart.Series();
			series1.setName("2003");
			series1.getData().add(new XYChart.Data(25601.34, "Austria"));
			series1.getData().add(new XYChart.Data(20148.82, "Brazil"));
			series1.getData().add(new XYChart.Data(10000, "France"));
			series1.getData().add(new XYChart.Data(35407.15, "Italy"));
			series1.getData().add(new XYChart.Data(12000, "USA"));

			XYChart.Series series2 = new XYChart.Series();
			series2.setName("2004");
			series2.getData().add(new XYChart.Data(57401.85, "Austria"));
			series2.getData().add(new XYChart.Data(41941.19, "Brazil"));
			series2.getData().add(new XYChart.Data(45263.37, "France"));
			series2.getData().add(new XYChart.Data(117320.16, "Italy"));
			series2.getData().add(new XYChart.Data(14845.27, "USA"));

			XYChart.Series series3 = new XYChart.Series();
			series3.setName("2005");
			series3.getData().add(new XYChart.Data(45000.65, "Austria"));
			series3.getData().add(new XYChart.Data(44835.76, "Brazil"));
			series3.getData().add(new XYChart.Data(18722.18, "France"));
			series3.getData().add(new XYChart.Data(17557.31, "Italy"));
			series3.getData().add(new XYChart.Data(92633.68, "USA"));


			stage.setTitle("New Window");
			stage.setScene(scene);
			stage.show();
		} catch (IOException e) {
			Logger logger = Logger.getLogger(getClass().getName());
			logger.log(Level.SEVERE, "Failed to create new Window.", e);
		}
	}
}
