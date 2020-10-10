package com.approval.demo.Controller;

/**
 * 工作流模型
 */

import com.alibaba.fastjson.JSONObject;
import com.approval.demo.model.ActvitiModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.RepositoryService;
//import org.activiti.engine.impl.util.json.JSONObject;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/model")
public class ActivitiController {

    @Autowired
    RepositoryService repositoryService;
    @Autowired
    ObjectMapper objectMapper;

    /**
     * 新建一个空模型
     * @return
     * @throws UnsupportedEncodingException
     */
    @PostMapping("/addModel")
    public JSONObject addModel(@Valid ActvitiModel modul) throws UnsupportedEncodingException {
        //初始化一个空模型
        Model model = repositoryService.newModel();
        int revision = 1;
        ObjectNode modelNode = objectMapper.createObjectNode();
        modelNode.put(ModelDataJsonConstants.MODEL_NAME, modul.getName());
        modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, modul.getDescription());
        modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);

        model.setName(modul.getName());
        model.setKey(modul.getKey());
        model.setCategory(modul.getCategory());
        model.setMetaInfo(modelNode.toString());

        repositoryService.saveModel(model);
        String id = model.getId();

        //完善ModelEditorSource
        ObjectNode editorNode = objectMapper.createObjectNode();
        editorNode.put("id", "canvas");
        editorNode.put("resourceId", "canvas");
        ObjectNode stencilSetNode = objectMapper.createObjectNode();
        stencilSetNode.put("namespace",
                "http://b3mn.org/stencilset/bpmn2.0#");
        editorNode.put("stencilset", stencilSetNode);
        repositoryService.addModelEditorSource(id,editorNode.toString().getBytes("utf-8"));
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id",id);
        return jsonObject;
    }
    @GetMapping("/list")
    public JSONObject getList(@RequestParam(value = "pageSize") Integer pageSize, @RequestParam(value = "pageNum") Integer pageNum) {
        List<Model> list = repositoryService.createModelQuery().listPage(1000 * (pageNum - 1)
                , 1000);
        long count = repositoryService.createModelQuery().count();
        JSONObject json = new JSONObject();
        json.put("rows",list);
        json.put("total",count);
        return json;
    }

    /**
     * 部署模型
     * @param id
     * @return
     * @throws Exception
     */
    @PostMapping("/deploy/{id}")
    public JSONObject deploy(@PathVariable("id") String id) throws Exception{
        JSONObject json = new JSONObject();
        String aa = null;
        Model modelData = repositoryService.getModel(id);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());

        if (bytes == null) {
            json.put("success","未查询到该模型");
            return json;
        }

        JsonNode modelNode = new ObjectMapper().readTree(bytes);

        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        if(model.getProcesses().size()==0){
            json.put("success","您部署的是一个空模型");
            return json;
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

        //发布流程
        String processName = modelData.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(modelData.getName())
                .addString(processName, new String(bpmnBytes, "UTF-8"))
                .deploy();
        modelData.setDeploymentId(deployment.getId());
        repositoryService.saveModel(modelData);
        json.put("success","部署成功");
        return json;
    }

    /**
     * 删除模型
     * @param id
     * @return
     */
    @PostMapping("/delete/{id}")
    public void delete(@PathVariable("id") String id){
        repositoryService.deleteModel(id);
    }

}
