package pku;

import pku.ByteMessage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MessageStore {
	static final MessageStore store = new MessageStore();

	// 消息存储
	HashMap<String, DataOutputStream> map_Out = new HashMap<>();          //  输出流
	//volatile int producerNum = 0;
	HashMap<String, DataInputStream> map_In = new HashMap<>();         //  输入流 


	
	
	private static class change_Headers_Key{
		/*
		 * 下面的匿名内部类是初始化 索引-固定头部-分类的 映射关系
		 */
		
		public static final  HashMap<Byte, String> indexToString = new HashMap<Byte, String>() {    // key是索引，value是固定头部 ？ 
			{
				put((byte) 1, "MessageId");
				put((byte) 2, "Topic");
				put((byte) 3, "BornTimestamp");
				put((byte) 4, "BornHost");
				put((byte) 5, "StoreTimestamp");
				put((byte) 6, "StoreHost");
				put((byte) 7, "StartTime");
				put((byte) 8, "StopTime");
				put((byte) 9, "Timeout");
				put((byte) 10, "Priority");
				put((byte) 11, "Reliability");
				put((byte) 12, "SearchKey");
				put((byte) 13, "ScheduleExpression");
				put((byte) 14, "ShardingKey");
				put((byte) 15, "ShardingPartition");
				put((byte) 16, "TraceId");
			}
		};
		public static final HashMap<String, Short> stringToClass = new HashMap<String, Short>() {   
			{                                          // 此处的 value 是 数字，表示是属于哪个类型  
				put("MessageId", (short) 3);             // int
				put("Topic", (short) 4);                 // String 
				put("BornTimestamp", (short) 1);        // long  
				put("BornHost", (short) 4);              // String 
				put("StoreTimestamp", (short) 1);        // long 
				put("StoreHost", (short) 4);             // String 
				put("StartTime", (short) 1);            // long  
				put("StopTime", (short) 1);            // long  
				put("Timeout", (short) 3);               // int 
				put("Priority", (short) 3);                // int 
				put("Reliability", (short) 3);              // int  
				put("SearchKey", (short) 4);                // String 
				put("ScheduleExpression", (short) 4);        // String 
				put("ShardingKey", (short) 2);            // double 
				put("ShardingPartition", (short) 2);          // double 
				put("TraceId", (short) 4);                  // String  
			}
		};
		public static final HashMap<String, Byte> stringToClassByte = new HashMap<String, Byte>() {     // value 表明索引
			{
				put("MessageId", (byte) 1);                      
				put("Topic", (byte) 2);                           
				put("BornTimestamp", (byte) 3);
				put("BornHost", (byte) 4);
				put("StoreTimestamp", (byte) 5);
				put("StoreHost", (byte) 6);
				put("StartTime", (byte) 7);
				put("StopTime", (byte) 8);
				put("Timeout", (byte) 9);
				put("Priority", (byte) 10);
				put("Reliability", (byte) 11);
				put("SearchKey", (byte) 12);
				put("ScheduleExpression", (byte) 13);
				put("ShardingKey", (byte) 14);
				put("ShardingPartition", (byte) 15);
				put("TraceId", (byte) 16);
			}
		};
		
		public static final HashMap<Byte, Byte> indexToClass = new HashMap<Byte, Byte>() {   //  索引  到  类型 的  key-value 对
			{
				put((byte) 1, (byte) 3);                            // int
				put((byte) 2, (byte) 4);                            // String 
				put((byte) 3, (byte) 1);                            // long 
				put((byte) 4, (byte) 4);                            // String 
				put((byte) 5, (byte) 1);                            // long 
				put((byte) 6, (byte) 4);                            // String 
				put((byte) 7, (byte) 1);                            // long 
				put((byte) 8, (byte) 1);                            // long 
				put((byte) 9, (byte) 3);                            // int 
				put((byte) 10, (byte) 3);                           // int 
				put((byte) 11, (byte) 3);                           // int 
				put((byte) 12, (byte) 4);                           // String 
				put((byte) 13, (byte) 4);                           // String 
				put((byte) 14, (byte) 2);                           // double 
				put((byte) 15, (byte) 2);                           // double
				put((byte) 16, (byte) 4);                           // String
			}
		};
	}
	
	
	
	
	
	public synchronized void flush()  throws IOException{
		Producer.count --;
		int count = Producer.count ;
		if( count != 0)
			return ;
		
		for(String head : map_Out.keySet() ){
			map_Out.get(head).close();
		}
		
	}

	// push
	public void push(ByteMessage msg, String topic) throws IOException {
		if (msg == null) {
			return;
		}
		DataOutputStream temp_Out;     // 输出流 ， 暂时 
		synchronized (map_Out) {     // 锁 ， 一个线程调用此代码块时， map_Out 会加锁，另外的线程就不能调用此代码块
			if (!map_Out.containsKey(topic)) {    // topic 无 对应的输出流 
				temp_Out = new DataOutputStream(
						new BufferedOutputStream(new FileOutputStream("./data/" + topic, true)));    // true表示添加而不是覆盖 ， 路径最后是 topic 实现多个输出流
				map_Out.put(topic, temp_Out);          // 把新的输出流 加入 输出流的 map 中 
				
				
	/*			FileOutputStream fs = new FileOutputStream("./data/" + topic, true);
				temp_Out = new DataOutputStream( fs );                此处是优化的点，居然可以提高push的效率近4倍？  为什么会这样？
				map_Out.put(topic, temp_Out);
						*/
			} else {
				temp_Out = map_Out.get(topic);         // 取得输出流 
			}
		}
		
		// 写入消息
		byte[] body = null;
		byte head_Num = (byte) msg.headers().keySet().size();   // 此处就是得到已经出现过的消息的 固定的头 组成的Set 集合的 大小      对于什么而言的已经出现？？
		byte isCompress;
		if (msg.getBody().length > 256) {     // 消息的 body的 byte数组 大于 1024 
			body = compress(msg.getBody());   // 对 body 压缩
			isCompress = 1;       // 记录被压缩了 
		}
		else {
			body=msg.getBody();      // 不压缩了
			isCompress = 0;
		}

		synchronized (temp_Out) {        //  加锁，只能一个线程访问 temp_Out 对象 
			temp_Out.writeByte(head_Num);                    //  写入  已经出现过的消息的 固定的头 组成的Set 集合的 大小 
			for (String key : msg.headers().keySet()) {     //  遍历已经出现过的 固定头部  
				
				temp_Out.writeByte(change_Headers_Key.stringToClassByte.get(key));         // 得到 某个 固定头部 对应的  byte类型的索引    并且写入 
				
		
				
				switch (change_Headers_Key.stringToClass.get(key)) {             // 此处 得到 short 类型的 数字化 类型 
				case 1:
					temp_Out.writeLong(msg.headers().getLong(key));               // 写入  long 类型的 topic 
					break;
				case 2:
					temp_Out.writeDouble(msg.headers().getDouble(key));               // 写入 double 类型的 topic
					break;
				case 3:
					temp_Out.writeInt(msg.headers().getInt(key));                  // 写入 int 类型的 topic
					break;
				case 4:
					temp_Out.writeUTF(msg.headers().getString(key));            // 写入 String 类型的 topic     此处是用输出流的 writeUTF
					break;
				}
			}
			temp_Out.writeByte(isCompress);           //  isCompress = 0 或者 1  表明 是否被压缩 
			temp_Out.writeShort(body.length);         // 写入 body，也就是 数据部分 的长度  用 short是为了节约 
			temp_Out.write(body);			          // 写入全部的 body 数据
		}
	}

	public ByteMessage pull(String queue, String topic) throws IOException {
		
		String queue_Topic = queue + " " + topic;                     // 此处 k 是 queue + topic 
		
		DataInputStream temp_In;                               // 
		
		if (!map_In.containsKey(queue_Topic)) {                       // 如果 输入流 的 Map  没包含 k 
			try {
				temp_In = new DataInputStream(new BufferedInputStream(new FileInputStream("./data/" + topic)));    // 建立 输入流 
			} catch (FileNotFoundException e) {
				// e.printStackTrace();
				return null;
			}
			synchronized (map_In) {             // 加锁，只能让一个线程调用  输入流的 put 
				map_In.put(queue_Topic , temp_In);             //  输入流 temp_In 添加进 输入流的 map 
			}
		} else {                                  // 否则
			temp_In = map_In.get(queue_Topic);                // 根据  queue + topic  作为 map 的 key 得到输入流 
		}

		if (temp_In.available() != 0) {                  // 此方法是返回流中实际可以读取的字节数目  
			// 读入消息
			ByteMessage msg = new DefaultMessage(null);           // 无参构造 
			//short head_Type;
			byte head_Type ;
			byte head_Num = temp_In.readByte();              // readByte() 返回的是 所读取的一个 byte     见78 行 
			for (int i = 0; i < head_Num; i++) {

				                                          //  接下来就是读取依此出现过的 固定头部 
				head_Type = temp_In.readByte();                //  在循环中获取 固定头部的  byte类型的 下标 
				switch (change_Headers_Key.indexToClass.get(head_Type)) {     //  short 类型的 分类 
				case  1:
					msg.putHeaders(change_Headers_Key.indexToString.get(head_Type), temp_In.readLong());  // 生成 message头部，key 是 short的分类，value 是 topic 
					break;                                                                         // 为什么 key 是分类？ 
				case  2:
					msg.putHeaders(change_Headers_Key.indexToString.get(head_Type), temp_In.readDouble());
					break;
				case  3:
					msg.putHeaders(change_Headers_Key.indexToString.get(head_Type), temp_In.readInt());
					break;
				case  4:
					msg.putHeaders(change_Headers_Key.indexToString.get(head_Type), temp_In.readUTF());   // 此处是用输入流的 readUTF() 
					break;
				}
			}
			msg.putHeaders("Topic", topic);         // 为什么这里是设置一个头部用 Topic ？                    
			byte is_Compress = temp_In.readByte();           // 读一个byte 表示 是否被压缩了                       这些见99行
			short length = temp_In.readShort();             //  读一个 short ，代表 data 的长度 ？
			byte[] data = new byte[length];                 // 用 byte数组 存储 data 
			temp_In.read(data);             
			if (is_Compress == 1) {                     // 如果压缩了
				msg.setBody(uncompress(data));      // 解压，并且 setBody
			} else {                         
				msg.setBody(data);                        // 直接 setBody
			}
			return msg;           
		} else {                                   // 没有可读的消息了 
			return null;
		}
	}

	public static byte[] compress(byte[] data) {
		byte[] buffer = null;
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			GZIPOutputStream gzip = new GZIPOutputStream(bos);
			gzip.write(data);
			gzip.finish();
			gzip.close();
			buffer = bos.toByteArray();
			bos.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return buffer;
	}

	public static byte[] uncompress(byte[] data) {
		byte[] buffer = null;
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(data);
			GZIPInputStream gzip = new GZIPInputStream(bis);
			byte[] buf = new byte[256];
			int num = -1;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while ((num = gzip.read(buf, 0, buf.length)) != -1) {
				baos.write(buf, 0, num);
			}
			buffer = baos.toByteArray();
			baos.flush();
			baos.close();
			gzip.close();
			bis.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return buffer;
	}

}
