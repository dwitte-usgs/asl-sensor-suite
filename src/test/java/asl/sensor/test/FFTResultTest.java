package asl.sensor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.util.Pair;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Before;
import org.junit.Test;
import asl.sensor.input.DataBlock;
import asl.sensor.input.InstrumentResponse;
import asl.sensor.utils.FFTResult;
import asl.sensor.utils.ReportingUtils;
import asl.sensor.utils.TimeSeriesUtils;
import edu.iris.dmc.seedcodec.CodecException;
import edu.sc.seis.seisFile.mseed.SeedFormatException;

public class FFTResultTest {

  public static String folder = TestUtils.DL_DEST_LOCATION + TestUtils.SUBPAGE;

  @Before
  public void getReferencedData() {

    // place in sprockets folder under 'from-sensor-test/[test-name]'

    String refSubfolder = TestUtils.SUBPAGE + "cowi-multitests/";
    String filename = "C100823215422_COWI.LHx";
    String filename2 = "DT000110.LH1";
    try {
      TestUtils.downloadTestData(refSubfolder, filename, refSubfolder, filename);
      TestUtils.downloadTestData(refSubfolder, filename2, refSubfolder, filename2);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    refSubfolder = TestUtils.SUBPAGE + "bandfilter-test/";
    filename = "00_LHZ.512.seed";
    try {
      TestUtils.downloadTestData(refSubfolder, filename, refSubfolder, filename);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  //@Test
  public void testComplexDivision() {
    // test is commented out because it is slow and unlikely to regress
    // because i and j would be the most intractable possible iteration vars
    for (int a = 1; a < 100000; ++a) {
      for (int b = a; b < 100000; ++b) {
        Complex numer = new Complex(a, b);
        Complex denom = new Complex(b, -a);
        Complex unit = numer.divide(numer); // expect 1
        Complex imag = numer.divide(denom); // expect i
        assertEquals(unit.getReal(), 1., 1E-15);
        assertEquals(unit.getImaginary(), 0., 1E-15);
        assertEquals(imag.getReal(), 0., 1E-15);
        assertEquals(imag.getImaginary(), 1., 1E-15);
      }
    }
  }

  // @Test
  public void testOddityWithCOWI() {
    String filename = folder + "cowi-multitests/C100823215422_COWI.LHx";
    String dataname = "US_COWI_  _LHN";
    try {
      PrintWriter out;
      DataBlock db = TimeSeriesUtils.getTimeSeries(filename, dataname);
      OffsetDateTime dt = OffsetDateTime.ofInstant(db.getStartInstant(), ZoneOffset.UTC);;
      //System.out.println(dt);
      dt = dt.withDayOfYear(236).withHour(0).withMinute(0).withSecond(0);
      //System.out.println(dt);
      long start = dt.toInstant().toEpochMilli();
      // dt = dt.withHour(15);
      long end = db.getEndTime();
      db.trim(start, end);
      double[] data = db.getData();

      long interval = 1;
      double sps = Math.min(1., TimeSeriesUtils.ONE_HZ_INTERVAL / interval);
      double low = 1./8; // filter from 8 seconds interval
      double high = 1./3; // up to 3 seconds interval

      data = TimeSeriesUtils.demean(data);
      data = TimeSeriesUtils.detrend(data);

      out = new PrintWriter("testResultImages/" + dataname + "-unfiltered.txt");
      out.write( Arrays.toString(data) );
      data = FFTResult.bandFilter(data, sps, low, high);
      out.close();
      out = new PrintWriter("testResultImages/" + dataname + "-filtered.txt");
      out.write( Arrays.toString(data) );
      out.close();

    } catch (FileNotFoundException | SeedFormatException | CodecException e) {
      e.printStackTrace();
      fail();
    }

  }

  @Test
  public void cosineTaperTest() {
    double[] x = { 5, 5, 5, 5, 5 };
    double[] toTaper = x.clone();
    double[] tapered = { 0d, 4.5d, 5d, 4.5d, 0d };

    double power = FFTResult.cosineTaper(toTaper, 0.25);

    assertEquals(new Double(Math.round(power)), new Double(4));

    for (int i = 0; i < x.length; i++) {
      // precision to nearest tenth?
      assertEquals(toTaper[i], tapered[i], 0.1);
    }
  }

  @Test
  public void fftInversionTest() {
    double[] timeSeries = {10, 11, 12, 11, 10, 11, 12, 11, 10, 11, 12};

    int padSize = 2;
    while (padSize < timeSeries.length) {
      padSize *= 2;
    }

    double[] paddedTS = new double[padSize];
    for (int i = 0; i < timeSeries.length; ++i) {
      paddedTS[i] = timeSeries[i];
    }

    // System.out.println(paddedTS.length);

    FastFourierTransformer fft =
        new FastFourierTransformer(DftNormalization.UNITARY);

    Complex[] frqDomn = fft.transform(paddedTS, TransformType.FORWARD);

    padSize = frqDomn.length/2 + 1;
    // System.out.println(padSize);

    Complex[] trim = new Complex[padSize];

    for (int i = 0; i < trim.length; ++i) {
      trim[i] = frqDomn[i];
    }

    padSize = (trim.length - 1) * 2;

    // System.out.println(padSize);

    Complex[] frqDomn2 = new Complex[padSize];

    for (int i = 0; i < padSize; ++i) {
      if (i < trim.length) {
        frqDomn2[i] = trim[i];
      } else {
        int idx = padSize - i;
        frqDomn2[i] = trim[idx].conjugate();
      }

      // System.out.println(frqDomn[i]+"|"+frqDomn2[i]);

    }

    Complex[] inverseFrqDomn = fft.transform(frqDomn2, TransformType.INVERSE);
    double[] result = new double[timeSeries.length];

    for (int i = 0; i < timeSeries.length; ++i) {
      result[i] = Math.round( inverseFrqDomn[i].getReal() );
      // System.out.println( result[i] + "," + inverseFrqDomn[i].getReal() );
      assertEquals(timeSeries[i], result[i], 0.1);
    }

  }

  @Test
  public void fftZerosTestMultitaper() {
    long interval = TimeSeriesUtils.ONE_HZ_INTERVAL;
    double[] data = new double[1000];
    // likely unnecessary loop, double arrays initialized at 0
    for (int i = 0; i < data.length; ++i) {
      data[i] = 0.;
    }
    FFTResult fftr = FFTResult.spectralCalcMultitaper(data, data, interval);
    Complex[] values = fftr.getFFT();
    for (Complex c : values) {
      assertTrue(c.equals(Complex.ZERO));
    }
  }

  @Test
  public void fftZerosTestWelch() {
    long interval = TimeSeriesUtils.ONE_HZ_INTERVAL;
    double[] data = new double[1000];
    // likely unnecessary loop, double arrays initialized at 0
    for (int i = 0; i < data.length; ++i) {
      data[i] = 0.;
    }
    FFTResult fftr = FFTResult.spectralCalc(data, data, interval);
    Complex[] values = fftr.getFFT();
    for (Complex c : values) {
      assertTrue(c.equals(Complex.ZERO));
    }
  }

  @Test
  public void lowPassFilterTest() {
    double[] timeSeries = new double[400];

    for (int i = 0; i < timeSeries.length; ++i) {
      if (i % 2 == 0) {
        timeSeries[i] = -10;
      } else
        timeSeries[i] = 10;
    }

    double sps = 40.;

    double[] lowPassed = FFTResult.bandFilter(timeSeries, sps, 0.5, 1.5);
    //System.out.println(Arrays.toString(lowPassed));
    for (int i = 1; i < (lowPassed.length - 1); ++i) {
      assertTrue( Math.abs( lowPassed[i] ) < 1. );
    }

  }

  @Test
  public void rangeCopyTest() {

    Number[] numbers = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};

    int low = 5;
    int high = 9;

    List<Number> numList = Arrays.asList(numbers);
    List<Number> subseq = new ArrayList<Number>(numList.subList(low, high));

    for (int i = 0; i < subseq.size(); ++i) {
      int fullListIdx = i + low;
      assertEquals( numList.get(fullListIdx), subseq.get(i) );
    }

    for (int i = 0; i < subseq.size(); ++i) {
      Number temp = subseq.get(i);
      temp = 2000;
      assertNotEquals(subseq.get(i),temp); // can't try to change "in-place"
      subseq.set(i, 100);
    }

    for (int i = 0; i < subseq.size(); ++i) {
      int fullListIdx = i + low;
      assertNotEquals( numList.get(fullListIdx), subseq.get(i) );
    }

  }

  //@Test
  public void showMultitaperPlot() {
    final int TAPERS = 12;
    StringBuilder sb = new StringBuilder();
    double[][] taper = FFTResult.getMultitaperSeries(512, TAPERS);
    XYSeriesCollection xysc = new XYSeriesCollection();
    for (int j = 0; j < taper.length; ++j) {
      XYSeries xys = new XYSeries("Taper " + j);
      double[] taperLine = taper[j];
      //System.out.println("TAPER LINE LEN: " + taperLine.length);
      //System.out.println("LAST VALUE: " + taperLine[taperLine.length - 1]);
      for (int i = 0; i < taperLine.length; ++i) {
        xys.add(i, taperLine[i]);
        sb.append(taperLine[i]);
        if ( i + 1 < taperLine.length) {
          sb.append(", ");
        }
      }
      sb.append("\n");
      xysc.addSeries(xys);
    }

    JFreeChart chart =
        ChartFactory.createXYLineChart("MULTITAPER", "taper series index",
            "taper value", xysc);

    BufferedImage bi = ReportingUtils.chartsToImage(1280, 960, chart);
    try {

      String testResultFolder = "testResultImages/";
      File folder = new File(testResultFolder);
      if ( !folder.exists() ) {
        System.out.println("Writing directory " + testResultFolder);
        folder.mkdirs();
      }

      File file = new File(testResultFolder + "multitaper plot.png");

      String testResult =
          testResultFolder + "multitaper_ascii.csv";
      PrintWriter out = new PrintWriter(testResult);
      out.println( sb.toString() );
      out.close();

      ImageIO.write( bi, "png", file );
    } catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }


  @Test
  public void testBandFilter() {
    String name = folder + "bandfilter-test/00_LHZ.512.seed";
    double[] testAgainst = new double[]{-50394.9143358, -111785.107014, -18613.4142884,
        143117.116357, 141452.164593, 6453.3516971, -79041.0146413, -58317.1285426, -8621.19465151,
        12272.6705308};
    DataBlock db;
    try {
      db = TimeSeriesUtils.getFirstTimeSeries(name);
      double sps = db.getSampleRate();
      assertEquals(sps, 1.0, 1E-10);
      double[] data = db.getData();
      double[] taper = new double[data.length];
      for (int i = 0; i < taper.length; ++i) {
        taper[i] = 1.;
      }
      FFTResult.cosineTaper(taper, 1.);
      double[] toFilter = new double[data.length];
      assertEquals(86400, data.length);
      /*
      for (int i = 0; i < data.length; ++i) {
        toFilter[i] = taper[i] * data[i];
      }
       */
      toFilter = data;
      double[] testThis = FFTResult.bandFilter(toFilter, sps, 1./8., 1./4.);
      for (int i = 0; i < testAgainst.length; ++i) {
        assertEquals(testThis[i], testAgainst[i], 1E-6);
      }
    } catch (FileNotFoundException | SeedFormatException | CodecException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      fail();
    }

  }

  @Test
  public void testTaperCurve() {
    int length = 21600;
    double width = 0.025;
    double[] taper = FFTResult.getCosTaperCurve(length, width);
    double[] testAgainst = new double[]{
        0.0, 8.49299745992e-06, 3.39717013156e-05, 7.64352460048e-05, 0.000135882188956,
        0.00021231051064, 0.000305717614632, 0.000416100327709,  0.000543454899949,
        0.000687777004865, 0.000849061739547, 0.00102730362483, 0.00122249660549
    };
    for (int i = 0; i < testAgainst.length; ++i) {
      assertEquals(testAgainst[i], taper[i], 5E-6);
    }
  }

  @Test
  public void PSDWindowTest() {
    String dataName = folder + "psd-check/" + "00_LHZ.512.seed";
    try {
      DataBlock db = TimeSeriesUtils.getFirstTimeSeries(dataName);
      double[] list1 = db.getData();
      assertEquals(86400, list1.length);
      int range = list1.length/4;
      assertEquals(21600, range);
      int slider = range/4;
      assertEquals(5400, slider);
      int padding = 2;
      while (padding < range) {
        padding *= 2;
      }
      assertEquals(32768, padding);
      // double period = 1.0 / TimeSeriesUtils.ONE_HZ_INTERVAL;
      // period *= db.getInterval();
      // int singleSide = padding / 2 + 1;
      // double deltaFreq = 1. / (padding * period);
      int segsProcessed = 0;
      int rangeStart = 0;
      int rangeEnd = range;
      while ( rangeEnd <= list1.length ) {

        // give us a new list we can modify to get the data of
        double[] toFFT =
            Arrays.copyOfRange(list1, rangeStart, rangeEnd);
        // FFTResult.cosineTaper(toFFT, 0.05);
        assertEquals(range, toFFT.length);
        Pair<Complex[], Double> tempResult = FFTResult.getSpectralWindow(toFFT, padding);
        double cos = tempResult.getSecond();
        assertEquals(20925, cos, 1);
        Complex[] result = tempResult.getFirst();
        if (segsProcessed == 0) {
          System.out.println(cos);
          System.out.println( Arrays.toString( Arrays.copyOfRange(toFFT, 0, 10) ) );
          System.out.println( Arrays.toString( Arrays.copyOfRange(result, 0, 10) ) );
        }

        ++segsProcessed;
        rangeStart  += slider;
        rangeEnd    += slider;

      }
      assertEquals(segsProcessed, 13);

    } catch (SeedFormatException | CodecException | IOException e) {
      e.printStackTrace();
      fail();
    }
}

  @Test
  public void PSDCalcTest() {
    String dataName = folder + "psd-check/" + "00_LHZ.512.seed";
    try {
      DataBlock db = TimeSeriesUtils.getFirstTimeSeries(dataName);
      assertEquals(86400, db.getData().length);
      // InstrumentResponse ir = new InstrumentResponse(respName);
      FFTResult psd = FFTResult.spectralCalc(db, db);
      Complex[] spect = psd.getFFT();
      System.out.println(spect.length);
      for (int i = 0; i < 10; ++i) {
        System.out.println(spect[i]);
      }
      assertEquals(spect.length, 16385);
      double deltaFreq = psd.getFreq(1);
      int lowIdx = (int) Math.ceil(1./(deltaFreq * 5.));
      int highIdx = (int) Math.floor(1./(deltaFreq * 3.));
      Complex[] spectTrim = Arrays.copyOfRange(spect, lowIdx, highIdx);
      double[] psdAmp = new double[spectTrim.length];
      for (int i = 0; i < spectTrim.length; ++i) {
        psdAmp[i] = 10 * Math.log10(spectTrim[i].abs());
      }
      double mean = TimeSeriesUtils.getMean(psdAmp);
      assertEquals(55.314, mean, 1E-2);
    } catch (SeedFormatException | CodecException | IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  @Test
  public void PSDCalcTestWithResp() {
    String dataName = folder + "psd-check/" + "00_LHZ.512.seed";
    String respName = folder + "psd-check/" + "RESP.IU.ANMO.00.LHZ";
    try {
      DataBlock db = TimeSeriesUtils.getFirstTimeSeries(dataName);
      InstrumentResponse ir = new InstrumentResponse(respName);
      FFTResult psd = FFTResult.crossPower(db, db, ir, ir);
      Complex[] spect = psd.getFFT();
      double deltaFreq = psd.getFreq(1);
      int lowIdx = (int) Math.floor(1./(deltaFreq * 5.));
      int highIdx = (int) Math.ceil(1./(deltaFreq * 3.));
      Complex[] spectTrim = Arrays.copyOfRange(spect, lowIdx, highIdx);
      double[] psdAmp = new double[spectTrim.length];
      for (int i = 0; i < spectTrim.length; ++i) {
        psdAmp[i] = 10 * Math.log10(spectTrim[i].abs());
      }
      double mean = TimeSeriesUtils.getMean(psdAmp);
      assertEquals(55.314, mean, 1E-2);
    } catch (SeedFormatException | CodecException | IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  @Test
  public void spectrumTest() {

    long interval = TimeSeriesUtils.ONE_HZ_INTERVAL;
    long timeStart = 0L;
    String name1 = "XX_FAKE_LH1_00";
    String name2 = "XX_FAKE_LH2_00";
    int len = 10000;
    double[] timeSeries = new double[len];
    double[] secondSeries = new double[len];

    for (int i = 0; i < len; ++i) {
      timeSeries[i] = Math.sin(i);
      secondSeries[i] = Math.sin(i) + Math.sin(2 * i);
    }

    DataBlock db = new DataBlock(timeSeries, interval, name1, timeStart);
    DataBlock db2 = new DataBlock(secondSeries, interval, name2, timeStart);
    FFTResult fft = FFTResult.spectralCalc(db, db2);

    XYSeriesCollection xysc = new XYSeriesCollection();
    String name = name1+"_"+name2;
    XYSeries xysr = new XYSeries(name + " spectrum (Real part)");
    XYSeries xysi = new XYSeries(name + " spectrum (Imag part)");
    for (int i = 0; i < fft.size(); ++i) {

      double freq = fft.getFreq(i);
      if ( freq <= 0. ) {
        continue;
      }

      xysr.add( freq, fft.getFFT(i).getReal() );
      xysi.add( freq, fft.getFFT(i).getImaginary() );
    }
    // xysc.addSeries(xysr);
    xysc.addSeries(xysi);

    JFreeChart jfc = ChartFactory.createXYLineChart(
        "SPECTRUM TEST CHART",
        "frequency",
        "value of spectrum",
        xysc);

    ValueAxis x = new LogarithmicAxis("frequency");
    jfc.getXYPlot().setDomainAxis(x);

    BufferedImage bi = ReportingUtils.chartsToImage(640, 480, jfc);
    String currentDir = System.getProperty("user.dir");
    String testResultFolder = currentDir + "/testResultImages/";
    File dir = new File(testResultFolder);
    if ( !dir.exists() ) {
      dir.mkdir();
    }

    String testResult =
        testResultFolder + "spectrum.png";
    File file = new File(testResult);
    try {
      ImageIO.write(bi, "png", file);
    } catch (IOException e) {
      fail();
      e.printStackTrace();
    }

  }

  @Test
  public void testMultitaper() {
    int size = 2000;
    List<Double> timeSeries = new ArrayList<Double>();
    for (int i = 0; i < size; ++i) {
      if (i % 2 == 0) {
        timeSeries.add(-500.);
      } else {
        timeSeries.add(500.);
      }
    }

    final int TAPERS = 12;
    double[][] taper = FFTResult.getMultitaperSeries(size, TAPERS);
    for (int j = 0; j < taper.length; ++j) {
      double[] toFFT = new double[size];
      int l = toFFT.length-1; // last point
      double[] taperCurve = taper[j];
      //double taperSum = 0.;
      //System.out.println(j + "-th taper curve first point: " + taperCurve[0]);
      //System.out.println(j + "-th taper curve last point: " + taperCurve[l]);
      for (int i = 0; i < timeSeries.size(); ++i) {
        // taperSum += Math.abs(taperCurve[i]);
        double point = timeSeries.get(i).doubleValue();
        toFFT[i] = point * taperCurve[i];
      }
      //System.out.println(j + "-th tapered-data first point: " + toFFT[0]);
      //System.out.println(j + "-th tapered-data last point: " + toFFT[l]);

      assertEquals(0., toFFT[0], 1E-10);
      assertEquals(0., toFFT[l], 1E-10);
    }
  }

}
