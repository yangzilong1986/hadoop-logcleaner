package org.zach;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogCleaner {

    public static class LogParserMapper
            extends Mapper<LongWritable, Text, LongWritable, Text> {

        LogParser logParser = new LogParser();
        Text v2 = new Text();

        public void map(LongWritable key, Text value, Context context
                           ) throws java.io.IOException ,InterruptedException {
            final String[] parsed = logParser.parse(value.toString());

            //filter static log record
            if(parsed[2].startsWith("GET /static/") || parsed[2].startsWith("GET /uc_server")){
                return;
            }

            //delete 'GET /' or 'POST /' in record
            if(parsed[2].startsWith("GET /")){
                parsed[2] = parsed[2].substring("GET /".length());
            }
            else if(parsed[2].startsWith("POST /")){
                parsed[2] = parsed[2].substring("POST /".length());
            }

            //delete ' HTTP/1.1' in record
            if(parsed[2].endsWith(" HTTP/1.1")){
                parsed[2] = parsed[2].substring(0, parsed[2].length()-" HTTP/1.1".length());
            }

            v2.set(parsed[0]+"\t"+parsed[1]+"\t"+parsed[2]);
            context.write(key, v2);
        }
    }

    public static class LogYielderReducer
            extends Reducer<LongWritable, Text, Text, NullWritable> {
        public void reduce(LongWritable k2, java.lang.Iterable<Text> v2s, Context context
                           ) throws java.io.IOException ,InterruptedException {
            for (Text v2 : v2s) {
                context.write(v2, NullWritable.get());
            }
        }
    }

    public static void main(String[] args) throws Exception{
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "clean log");
        job.setJarByClass(LogCleaner.class);
        FileInputFormat.setInputPaths(job, args[0]);
        job.setMapperClass(LogParserMapper.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setReducerClass(LogYielderReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(NullWritable.class);
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }

    public static class LogParser {

        public static final SimpleDateFormat format =
                new SimpleDateFormat("d/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);
        public static final SimpleDateFormat dateformat1=
                new SimpleDateFormat("yyyyMMddHHmmss");

        public static void main(String[] args) throws ParseException {
            final String strDemo = "27.19.74.143 - - [30/May/2013:17:38:20 +0800]" +
                    " \"GET /static/image/common/faq.gif HTTP/1.1\" 200 1127";
            LogParser parser = new LogParser();
            final String[] array = parser.parse(strDemo);
            System.out.println("string demo： "+ strDemo);
            System.out.format("parsed string：  ip=%s, time=%s, url=%s, status=%s, traffic=%s",
                    array[0], array[1], array[2], array[3], array[4]);
        }
        /**
         * parse date time
         * @param string
         * @return
         * @throws ParseException
         */
        private Date parseDateFormat(String string){
            Date parse = null;
            try {
                parse = format.parse(string);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return parse;
        }
        /**
         * parse log record
         * @param line
         * @return array contains five elements: ip, time, url, status, traffic
         */
        public String[] parse(String line){
            String ip = parseIP(line);
            String time = parseTime(line);
            String url = parseURL(line);
            String status = parseStatus(line);
            String traffic = parseTraffic(line);

            return new String[]{ip, time ,url, status, traffic};
        }

        private String parseTraffic(String line) {
            final String trim = line.substring(line.lastIndexOf("\"")+1).trim();
            String traffic = trim.split(" ")[1];
            return traffic;
        }
        private String parseStatus(String line) {
            final String trim = line.substring(line.lastIndexOf("\"")+1).trim();
            String status = trim.split(" ")[0];
            return status;
        }
        private String parseURL(String line) {
            final int first = line.indexOf("\"");
            final int last = line.lastIndexOf("\"");
            String url = line.substring(first+1, last);
            return url;
        }
        private String parseTime(String line) {
            final int first = line.indexOf("[");
            final int last = line.indexOf("+0800]");
            String time = line.substring(first+1,last).trim();
            Date date = parseDateFormat(time);
            return dateformat1.format(date);
        }
        private String parseIP(String line) {
            String ip = line.split("- -")[0].trim();
            return ip;
        }
    }


}


