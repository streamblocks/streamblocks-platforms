package ch.epfl.vlsc.turnus.adapter.external;

import turnus.model.dataflow.*;

import java.util.HashMap;
import java.util.Map;

public class ArtExternals {

    public static final Map<String, Actor> externalActors = new HashMap<String, Actor>() {
        {
            put("art_Source_bin", getArtSource("art_Source_bin"));
            put("art_Source_byte", getArtSource("art_Source_byte"));
            put("art_Source_real", getArtSource("art_Source_real"));
            put("art_Source_txt", getArtSource("art_Source_txt"));
            put("art_Sink_txt", getArtSink("art_Sink_txt"));
            put("art_Display_yuv", getDisplayYuv());
            put("art_Display_yuv_width_height", getDisplayYuvWidthHeight());
        }
    };

    private static Actor getArtSource(String name) {
        DataflowFactory factory = DataflowFactory.eINSTANCE;
        Actor actor = factory.createActor();
        actor.setName(name);

        // -- Output Port : Out
        Port port = factory.createPort();
        port.setName("Out");
        actor.getOutputPorts().add(port);

        // -- State variables

        // -- Parameters as State Variables

        // -- filename
        Variable fileName = factory.createVariable();
        fileName.setName("fileName");
        fileName.setType(DataflowFactory.eINSTANCE.createTypeString());
        //actor.getVariables().add(fileName);

        // -- loops
        Variable loops = factory.createVariable();
        loops.setName("loops");
        TypeInt type = DataflowFactory.eINSTANCE.createTypeInt();
        type.setSize(32);
        loops.setType(type);
        //actor.getVariables().add(loops);

        // -- Action
        Action action = factory.createAction();
        action.setName("action");
        action.getOutputPorts().add(port);

        actor.getActions().add(action);

        return actor;
    }

    private static Actor getArtSink(String name) {
        DataflowFactory factory = DataflowFactory.eINSTANCE;
        Actor actor = factory.createActor();
        actor.setName(name);

        // -- Output Port : Out
        Port port = factory.createPort();
        port.setName("In");
        actor.getInputPorts().add(port);

        // -- State variables

        // -- Parameters as State Variables

        // -- filename
        Variable fileName = factory.createVariable();
        fileName.setName("fileName");
        fileName.setType(DataflowFactory.eINSTANCE.createTypeString());
        //actor.getVariables().add(fileName);



        // -- Action
        Action action = factory.createAction();
        action.setName("sink");
        action.getOutputPorts().add(port);

        actor.getActions().add(action);

        return actor;
    }

    private static Actor getDisplayYuv() {
        DataflowFactory factory = DataflowFactory.eINSTANCE;
        Actor actor = factory.createActor();
        actor.setName("art_Display_yuv");

        // -- Input port : In
        Port portIn = factory.createPort();
        portIn.setName("In");
        actor.getInputPorts().add(portIn);

        // -- Actor Parameters
        Variable title = factory.createVariable();
        title.setName("title");
        title.setType(DataflowFactory.eINSTANCE.createTypeString());
        //actor.getVariables().add(title);

        Variable width = factory.createVariable();
        width.setName("width");
        TypeInt type = DataflowFactory.eINSTANCE.createTypeInt();
        type.setSize(32);
        width.setType(type);
        //actor.getVariables().add(width);

        Variable height = factory.createVariable();
        height.setName("height");
        type = DataflowFactory.eINSTANCE.createTypeInt();
        type.setSize(32);
        height.setType(type);
        //actor.getVariables().add(height);

        // -- Actions
        Action read = factory.createAction();
        read.setName("read");
        read.getInputPorts().add(portIn);
        actor.getActions().add(read);

        return actor;
    }

    private static Actor getDisplayYuvWidthHeight() {
        DataflowFactory factory = DataflowFactory.eINSTANCE;
        Actor actor = factory.createActor();
        actor.setName("art_Display_yuv_width_height");

        // -- Input port : In
        Port portIn = factory.createPort();
        portIn.setName("In");
        actor.getInputPorts().add(portIn);

        Port portWidth = factory.createPort();
        portWidth.setName("WIDTH");
        actor.getInputPorts().add(portWidth);

        Port portHeight = factory.createPort();
        portHeight.setName("HEIGHT");
        actor.getInputPorts().add(portHeight);


        // -- Actor Parameters
        Variable title = factory.createVariable();
        title.setName("title");
        title.setType(DataflowFactory.eINSTANCE.createTypeString());
        //actor.getVariables().add(title);

        Variable width = factory.createVariable();
        width.setName("width");
        TypeInt type = DataflowFactory.eINSTANCE.createTypeInt();
        type.setSize(32);
        width.setType(type);
        //actor.getVariables().add(width);

        Variable height = factory.createVariable();
        height.setName("height");
        type = DataflowFactory.eINSTANCE.createTypeInt();
        type.setSize(32);
        height.setType(type);
        //actor.getVariables().add(height);

        // -- Actions
        Action startFrame = factory.createAction();
        startFrame.setName("startFrame");
        startFrame.getInputPorts().add(portWidth);
        startFrame.getInputPorts().add(portHeight);
        actor.getActions().add(startFrame);

        Action read = factory.createAction();
        read.setName("read");
        read.getInputPorts().add(portIn);
        actor.getActions().add(read);

        return actor;
    }


}
