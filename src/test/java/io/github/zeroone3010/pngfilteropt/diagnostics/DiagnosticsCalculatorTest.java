package io.github.zeroone3010.pngfilteropt.diagnostics;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsCalculatorTest {
    private final DiagnosticsCalculator calc = new DiagnosticsCalculator();
    @Test void allZerosShowsHighZeroAndLowDistinct(){var d=calc.calculate(img(List.of(row(0,PngFilter.NONE,new byte[]{0,0,0,0}),row(1,PngFilter.NONE,new byte[]{0,0,0,0}))));assertTrue(d.zeroPercentage()>80);assertEquals(1,d.distinctByteValues());assertTrue(d.longestIdenticalRun()>=10);} 
    @Test void repeatedRowsIncreaseRepeatSignals(){var d=calc.calculate(img(List.of(row(0,PngFilter.UP,new byte[]{1,2,3,4}),row(1,PngFilter.UP,new byte[]{1,2,3,4}),row(2,PngFilter.UP,new byte[]{1,2,3,4}))));assertEquals(2,d.rowsEqualToPrevious());assertTrue(d.repeatedFullRowCount()>=2);assertEquals(3,d.filterUsage().counts().get(PngFilter.UP));}
    @Test void randomishHasMoreDistinct(){var d=calc.calculate(img(List.of(row(0,PngFilter.SUB,new byte[]{1,11,21,31,41,51,61,71,81,91}))));assertTrue(d.distinctByteValues()>=8);assertTrue(d.zeroPercentage()<20);} 
    @Test void alternatingPatternShowsDirectionalSubstringRepeats(){byte[] b=new byte[256];for(int i=0;i<b.length;i++)b[i]=(byte)((i%2==0)?7:9);var d=calc.calculate(img(List.of(row(0,PngFilter.NONE,b))));assertTrue(d.repetitionMetrics().repeated16ByteSubstrings()>0);assertTrue(d.repetitionMetrics().repeated32ByteSubstrings()>0);} 
    @Test void lzDiagnosticsDifferentiateStreams(){
        byte[] zeros=new byte[5000];
        byte[] random=new byte[5000]; for(int i=0;i<random.length;i++) random[i]=(byte)(i*37+11);
        byte[] phrase=new byte[6000]; byte[] ptn="ABCDEF".getBytes(); for(int i=0;i<phrase.length;i++) phrase[i]=ptn[i%ptn.length];
        var dz=calc.calculate(img(List.of(row(0,PngFilter.NONE,zeros)))).lzParseDiagnostics();
        var dr=calc.calculate(img(List.of(row(0,PngFilter.NONE,random)))).lzParseDiagnostics();
        var dp=calc.calculate(img(List.of(row(0,PngFilter.NONE,phrase)))).lzParseDiagnostics();
        assertTrue(dz.matchCoveragePercent()>80); assertTrue(dr.matchCoveragePercent()<20); assertTrue(dp.matchTokenCount()>0);
    }

    private static FilteredImage img(List<FilteredRow> rows){return new FilteredImage(new RawImage(4,rows.size(),8,2,3,12,List.of()),rows);} private static FilteredRow row(int i,PngFilter f,byte[] b){return new FilteredRow(i,f,b);} }
