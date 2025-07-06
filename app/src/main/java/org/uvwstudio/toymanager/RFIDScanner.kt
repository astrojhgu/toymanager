package org.uvwstudio.toymanager

import android.util.Log
import com.gg.reader.api.dal.GClient
import com.gg.reader.api.dal.HandlerTagEpcLog
import com.gg.reader.api.protocol.gx.EnumG
import com.gg.reader.api.protocol.gx.LogBaseEpcInfo
import com.gg.reader.api.protocol.gx.MsgAppGetReaderInfo
import com.gg.reader.api.protocol.gx.MsgBaseGetPower
import com.gg.reader.api.protocol.gx.MsgBaseInventoryEpc
import com.gg.reader.api.protocol.gx.MsgBaseSetPower
import com.gg.reader.api.protocol.gx.MsgBaseStop
import java.io.FileReader
import java.io.FileWriter
import java.util.Hashtable

object RFIDScanner {
    // 这里写属性和方法
    var powered=false
    var rfPower=10
    var client=GClient()

    fun initDevice(){
        Log.d("RFID","open device")
        setDevPowerOn(true)
        //val baudRateList = intArrayOf(460800, 115200)
        val baudRateList = intArrayOf(115200)
        for (i in baudRateList) {
            val b: Boolean = openReader(i.toString() + "")
            Log.d("RFID", "trying to open Reader with baudrate=$i")
            if (b) {
                Log.d("RFID", "opened with baudrate $i")
                val getReaderInfo=MsgAppGetReaderInfo()
                client.sendSynMsg(getReaderInfo)
                if (getReaderInfo.rtCode == 0.toByte()) {
                    Log.d("RFID", getReaderInfo.readerSerialNumber)
                    Log.d("RFID", getReaderInfo.formatPowerOnTime)
                    Log.d("RFID", getReaderInfo.baseCompileTime)
                    //set_info_serialNo.setText(getReaderInfo.getReaderSerialNumber());
                    //set_info_powerTime.setText(getReaderInfo.getFormatPowerOnTime());
                    //set_info_baseCompareTime.setText(getReaderInfo.getBaseCompileTime());
                }

                break
            }
        }
        setRFPwr(rfPower)
    }

    private fun openReader(baudRate: String): Boolean {
        if (client.openAndroidSerial("/dev/ttyS3:$baudRate", 10)) {
            val stop = MsgBaseStop()
            client.sendSynMsg(stop)
            if (stop.rtCode.toInt() == 0) {
                return stop.rtCode.toInt() == 0
            } else {
                client.close()
            }
        }
        return false
    }

    fun queryRFPwr(): Hashtable<Int, Int>?{
        val msg= MsgBaseGetPower()
        client.sendSynMsgRetry(msg, 10,10)
        if (msg.rtCode.toInt()==0){
            Log.d("RFID", "RFID Power:")
            for ((k,v) in msg.dicPower){
                Log.d("RFID", "ANT_${k}=${v}")
            }
            return msg.dicPower
        }else{
            Log.e("RFID", "pwr query failed")
            Log.e("RFID", "${msg.rtMsg}")
            return null
        }
    }

    fun setRFPwr(pwr: Int){
        rfPower=pwr
        if (!powered){
            return
        }
        val msg= MsgBaseSetPower()
        msg.dicPower= Hashtable<Int, Int>().apply {
            put(1, pwr)
        }
        client.sendSynMsgRetry(msg, 10, 10)
        if (msg.rtCode.toInt()==0){
            Log.d("RFID", "RFID Set power succeeded")

        }else{
            Log.e("RFID", "pwr query failed")
            Log.e("RFID", "${msg.rtMsg}")
        }
    }




    fun closeDevice(){
        setDevPowerOn(false)
    }

    private fun setDevPowerOn(bState: Boolean){
        val state=if (bState)  "1" else "0"
        val s2 = "/proc/gpiocontrol/set_uhf"
        val s3 = "/proc/gpiocontrol/set_bd"
        val localFileWriterOn1=FileWriter(s2)
        localFileWriterOn1.write(state);
        localFileWriterOn1.close();

        Log.d("RFID", "power=" + state + " Path=" + s2);


        val localFileWriterOn = FileWriter(s3)
        localFileWriterOn.write(state)
        localFileWriterOn.close()
        Log.d("RFID", "power=$state Path=$s3")

        Thread.sleep(1500);
        powered=bState
    }




    fun startScan(callback: (String?, String) -> Unit ){
        println("UhfScanner started scanning")
        stopScan()
        client.onTagEpcLog = object : HandlerTagEpcLog {
            override fun log(s: String?, logBaseEpcInfo: LogBaseEpcInfo) {
                Log.d("RFID", "event")
                if (logBaseEpcInfo.result == 0) {
                    val epc = logBaseEpcInfo.epc
                    Log.d("RFID", "calling callback with s=$s epc=$epc")
                    callback(s, epc)
                    //Log.d("RFID", "RFID: $epc")
                }else{
                    Log.e("RFID", logBaseEpcInfo.rtMsg)
                }
            }
        }

        val msg= MsgBaseInventoryEpc()
        msg.setAntennaEnable(EnumG.AntennaNo_1);
        msg.setInventoryMode(EnumG.InventoryMode_Inventory);
        client.sendSynMsg(msg);
        if (msg.rtCode.toInt()==0){
            Log.d("RFID", "start scanning")
        }else{
            Log.e("RFID", "scan failed")
            Log.e("RFID", "${msg.rtMsg}")
        }



    }

    fun stopScan() {
        if (!powered){
            Log.e("RFID", "Not powered")
            return
        }


        //closeDevice()


        for(i in 0..10){
            val msg= MsgBaseStop()
            client.sendSynMsgRetry(msg, 10, 10)
            if (msg.rtCode.toInt() ==0){
                Log.d("RFID", "stop succeeded")
                break
            }else{
                Log.e("RFID", "stop failed")
                Log.e("RFID", "${msg.rtMsg}")
            }
        }
    }
}
