package se.kth.hopsworks.rest;


import org.apache.commons.beanutils.BeanUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.persistence.exceptions.DatabaseException;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import se.kth.bbc.project.Project;
import se.kth.hopsworks.controller.ResponseMessages;
import se.kth.hopsworks.filters.AllowedRoles;
import se.kth.hopsworks.hdfs.fileoperations.DistributedFsService;
import se.kth.hopsworks.workflows.*;
import se.kth.hopsworks.workflows.Node;
import se.kth.hopsworks.workflows.nodes.*;

import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.resource.spi.work.Work;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RequestScoped
@RolesAllowed({"SYS_ADMIN", "BBC_USER"})
@TransactionAttribute(TransactionAttributeType.NEVER)
public class WorkflowService {
    private final static Logger logger = Logger.getLogger(WorkflowService.class.
            getName());

    @EJB
    private WorkflowFacade workflowFacade;

    @EJB
    private NodeFacade nodeFacade;

    @EJB
    private EdgeFacade edgeFacade;

    @EJB
    private NoCacheResponse noCacheResponse;

    @Inject
    private NodeService nodeService;

    @Inject
    private EdgeService edgeService;

    @Inject
    private WorkflowExecutionService workflowExecutionService;

    public void setProject(Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    Project project;

    public WorkflowService(){

    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response index() throws AppException {
        Collection<Workflow> workflows = project.getWorkflowCollection();
        GenericEntity<Collection<Workflow>> workflowsList = new GenericEntity<Collection<Workflow>>(workflows) {};
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(workflowsList).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response create(
            Workflow workflow,
            @Context HttpServletRequest req) throws AppException {
        try {
            workflow.setProjectId(this.project.getId());
            workflowFacade.persist(workflow);
            workflowFacade.flush();


            RootNode root = new RootNode();
            root.setWorkflow(workflow);
            EndNode end = new EndNode();
            end.setWorkflow(workflow);
            BlankNode blank = new BlankNode();
            blank.setWorkflow(workflow);
            nodeFacade.persist(root);
            nodeFacade.persist(end);
            nodeFacade.persist(blank);
            nodeFacade.flush();

            Edge rootEdge = new Edge(root, blank);
            edgeFacade.save(rootEdge);

            Edge endEdge = new Edge(blank, end);
            edgeFacade.save(endEdge);
            edgeFacade.flush();

        }catch(EJBException | PersistenceException e){
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), e.getMessage());
        }

        JsonNode json = new ObjectMapper().valueToTree(workflowFacade.refresh(workflow));
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json.toString()).build();
    }

    @POST
    @Path("/import")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response create(
            @Context HttpServletRequest req) throws AppException {


        String xml = new String()
                .replaceAll("\n", "")
                .replaceAll(">\\s*<", "><");

        Workflow workflow = new Workflow();
        workflow.setProjectId(this.project.getId());

        /**/
        xml = xml.concat("<workflow-app name=\"bwork\" xmlns=\"uri:oozie:workflow:0.5\"><start to=\"ForkNode_e42a40bd-d385-4fd8-98ea-ad7363bdd08c\"/><fork name=\"ForkNode_e42a40bd-d385-4fd8-98ea-ad7363bdd08c\"><path start=\"SparkCustomN_0f2da6cf-aaa7-4f5a-96ba-bbd3b9b5a102\"/><path start=\"SparkCustomN_87a05d79-fc0d-4016-85c7-ba6c87c78ab2\"/></fork><action name=\"SparkCustomN_0f2da6cf-aaa7-4f5a-96ba-bbd3b9b5a102\"><spark xmlns=\"uri:oozie:spark-action:0.1\"><job-tracker>${jobTracker}</job-tracker><name-node>${nameNode}</name-node><prepare><delete path=\"/Projects/oozie/Resources/out\"/><mkdir path=\"/Projects/oozie/Resources/out\"/></prepare><master>${sparkMaster}</master><mode>${sparkMode}</mode><name/><class>org.apache.oozie.example.SparkFileCopy</class><jar>${nameNode}/Workflows/meb10000/bwork/1461855739000/lib/oozie-examples.jar</jar><spark-opts/><arg>/Projects/oozie/Resources/in/data.txt</arg><arg>/Projects/oozie/Resources/out</arg></spark><ok to=\"JoinNode_82e872a3-be2a-41cb-a9c4-5ebaa4033214\"/><error to=\"kill\"/></action><action name=\"SparkCustomN_87a05d79-fc0d-4016-85c7-ba6c87c78ab2\"><spark xmlns=\"uri:oozie:spark-action:0.1\"><job-tracker>${jobTracker}</job-tracker><name-node>${nameNode}</name-node><prepare/><master>${sparkMaster}</master><mode>${sparkMode}</mode><name/><class>org.apache.spark.examples.SparkPi</class><jar>${nameNode}/Workflows/meb10000/bwork/1461855739000/lib/spark-examples-1.5.2-hadoop2.4.0.jar</jar><spark-opts/></spark><ok to=\"JoinNode_82e872a3-be2a-41cb-a9c4-5ebaa4033214\"/><error to=\"kill\"/></action><join name=\"JoinNode_82e872a3-be2a-41cb-a9c4-5ebaa4033214\" to=\"DecisionNode_5f43385d-95bb-4f97-8425-ea6b82e972ef\"/><decision name=\"DecisionNode_5f43385d-95bb-4f97-8425-ea6b82e972ef\"><switch><case to=\"SparkCustomN_1318d8d2-79d3-4d38-87e3-ece59ffbdf9e\">${fs:fileSize('/usr/foo/myinputdir') gt 10 * GB}</case><default to=\"end\"/></switch></decision><action name=\"SparkCustomN_1318d8d2-79d3-4d38-87e3-ece59ffbdf9e\"><spark xmlns=\"uri:oozie:spark-action:0.1\"><job-tracker>${jobTracker}</job-tracker><name-node>${nameNode}</name-node><prepare/><master>${sparkMaster}</master><mode>${sparkMode}</mode><name/><class>org.apache.spark.examples.SparkPi</class><jar>${nameNode}/Workflows/meb10000/dwork/1461782319000/lib/spark-examples-1.5.2-hadoop2.4.0.jar</jar><spark-opts/></spark><ok to=\"end\"/><error to=\"kill\"/></action><kill name=\"kill\"><message>Workflow failed, error message[${wf:errorMessage(wf:lastErrorNode())}]</message></kill><end name=\"end\"/></workflow-app>");
        /**/

        try {
            nodeFacade.buildXml(xml, workflow);
        }catch(ParserConfigurationException | SAXException | ProcessingException | IOException | XPathException e){
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                    e.getMessage());
        }
        workflow = workflowFacade.refresh(workflow);
        JsonNode json = new ObjectMapper().valueToTree(workflow);
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json.toString()).build();

    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response show(
            @PathParam("id") Integer id) throws AppException {
        Workflow workflow;
        try{
            workflow = workflowFacade.find(id, project);
        }catch(EJBException e) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ResponseMessages.WORKFLOW_NOT_FOUND);
        }
        JsonNode json = new ObjectMapper().valueToTree(workflow);
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json.toString()).build();
    }

    @PUT
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response update(
            String stringParams,
            @PathParam("id") Integer id) throws AppException {
        try {
            Workflow workflow;
            try{
                workflow = workflowFacade.find(id, project);
            }catch(EJBException e) {
                throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                        ResponseMessages.WORKFLOW_NOT_FOUND);
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> paramsMap = mapper.convertValue(mapper.readTree(stringParams), Map.class);
            BeanUtils.populate(workflow, paramsMap);
            workflow = workflowFacade.merge(workflow);
            JsonNode json = new ObjectMapper().valueToTree(workflow);
            return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json.toString()).build();
        }catch(IOException | IllegalAccessException | InvocationTargetException | EJBException e){
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), e.getMessage());
        }
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public Response delete(
            @PathParam("id") Integer id) throws AppException {
        Workflow workflow;
        try{
            workflow = workflowFacade.find(id, project);
        }catch(EJBException e) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ResponseMessages.WORKFLOW_NOT_FOUND);
        }
        JsonNode json = new ObjectMapper().valueToTree(workflow);
        workflowFacade.remove(workflow);
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json.toString()).build();
    }

    @Path("{id}/nodes")
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public NodeService nodes(
            @PathParam("id") Integer id) throws AppException {
        Workflow workflow;
        try{
            workflow = workflowFacade.find(id, project);
        }catch(EJBException e) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ResponseMessages.WORKFLOW_NOT_FOUND);
        }
        this.nodeService.setWorkflow(workflow);

        return this.nodeService;
    }

    @Path("{id}/edges")
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public EdgeService edges(
            @PathParam("id") Integer id) throws AppException {
        Workflow workflow;
        try{
            workflow = workflowFacade.find(id, project);
        }catch(EJBException e) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ResponseMessages.WORKFLOW_NOT_FOUND);
        }
        this.edgeService.setWorkflow(workflow);

        return this.edgeService;
    }

    @Path("{id}/executions")
    @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
    public WorkflowExecutionService executions(
            @PathParam("id") Integer id) throws AppException {
        Workflow workflow;
        try{
            workflow = workflowFacade.find(id, project);
        }catch(EJBException e) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ResponseMessages.WORKFLOW_NOT_FOUND);
        }
        this.workflowExecutionService.setWorkflow(workflow);

        return this.workflowExecutionService;
    }
}
