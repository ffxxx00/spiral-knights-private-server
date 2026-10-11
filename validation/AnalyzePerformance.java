package validation;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

/** Summarizes local JFR recordings without exporting paths or event payloads. */
public final class AnalyzePerformance {
    public static void main(String[] args) throws Exception {
        Map<String, Long> execution = new HashMap<>(), allocation = new HashMap<>(), waiting = new HashMap<>();
        long pauses = 0; double pauseMs = 0, maxPauseMs = 0;
        long fileReads = 0, fileWrites = 0; double readMs = 0, writeMs = 0, maxReadMs = 0, maxWriteMs = 0;
        try (RecordingFile recording = new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String type = event.getEventType().getName();
                if (type.equals("jdk.ExecutionSample") || type.equals("jdk.NativeMethodSample")) {
                    String thread = event.getThread("sampledThread").getJavaName();
                    execution.merge(thread + " | " + stack(event), 1L, Long::sum);
                } else if (type.equals("jdk.ObjectAllocationSample")) {
                    allocation.merge(event.getClass("objectClass").getName() + " | " + stack(event),
                        event.getLong("weight"), Long::sum);
                } else if (type.equals("jdk.GCPhasePause")) {
                    double millis = event.getDuration().toNanos()/1e6;
                    pauses++; pauseMs += millis; maxPauseMs = Math.max(maxPauseMs,millis);
                } else if (type.equals("jdk.FileRead")) {
                    double millis = event.getDuration().toNanos()/1e6;
                    fileReads++; readMs += millis; maxReadMs = Math.max(maxReadMs,millis);
                } else if (type.equals("jdk.FileWrite")) {
                    double millis = event.getDuration().toNanos()/1e6;
                    fileWrites++; writeMs += millis; maxWriteMs = Math.max(maxWriteMs,millis);
                } else if (type.equals("jdk.ThreadPark") || type.equals("jdk.ThreadSleep")) {
                    waiting.merge(event.getThread().getJavaName() + " | " + stack(event),
                        event.getDuration().toNanos()/1_000_000L, Long::sum);
                }
            }
        }
        System.out.printf(Locale.ROOT,"GC pauses=%d totalMs=%.2f maxMs=%.2f%n",pauses,pauseMs,maxPauseMs);
        System.out.printf(Locale.ROOT,"Slow file reads=%d totalMs=%.2f maxMs=%.2f writes=%d totalMs=%.2f maxMs=%.2f%n",
            fileReads,readMs,maxReadMs,fileWrites,writeMs,maxWriteMs);
        top("Execution samples",execution); top("Sampled allocation bytes",allocation); top("Wait milliseconds",waiting);
    }
    private static String stack(RecordedEvent event) {
        RecordedStackTrace trace = event.getStackTrace();
        if (trace == null || trace.getFrames().isEmpty()) return "no stack";
        StringBuilder result = new StringBuilder();
        for (RecordedFrame frame : trace.getFrames().subList(0,Math.min(3,trace.getFrames().size()))) {
            if (result.length() > 0) result.append(" <- ");
            result.append(frame.getMethod().getType().getName()).append('.').append(frame.getMethod().getName());
        }
        return result.toString();
    }
    private static void top(String label, Map<String, Long> values) {
        System.out.println(label + ":");
        values.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(16)
            .forEach(entry -> System.out.println("  " + entry.getValue() + " " + entry.getKey()));
    }
}
