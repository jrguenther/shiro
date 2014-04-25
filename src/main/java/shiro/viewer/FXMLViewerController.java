/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package shiro.viewer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import shiro.Port;
import shiro.SubjunctiveParametricSystem;
import shiro.Value;
import shiro.definitions.SystemState;
import shiro.drawing.Canvas;
import shiro.drawing.MoveContext;

/**
 * FXML Controller class
 *
 * @author jeffreyguenther
 */
public class FXMLViewerController {

    @FXML
    private BorderPane root;
    @FXML
    private Button run;
    @FXML
    private TextArea code;
    @FXML
    private ListView<String> altsList;
    @FXML
    private Pane canvas;
    @FXML
    private TextArea console;
    @FXML
    private FlowPane gallery;
    @FXML
    private ScrollPane lightTableScrollPane;

    private SubjunctiveParametricSystem model;
    private Map<SystemState, Canvas> layers;
    private File currentFile;
    private Map<SystemState, WritableImage> snapshots;
    private Group lightTable;
    private MoveContext moveContext;
    private ImageView selectedTile;

    public void initialize() {
        model = new SubjunctiveParametricSystem();
        layers = new HashMap<>();
        addEventHandlersToList();
        altsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        currentFile = null;
        snapshots = new HashMap<>();
        lightTable = new Group();
        moveContext = new MoveContext();
        selectedTile = null;
        root.setBottom(null);
        
        
    }

    @FXML
    private void handleRun(ActionEvent event) {
        run();
    }
    
    public void run(){
        altsList.getItems().clear();
        gallery.getChildren().clear();
        lightTable.getChildren().clear();
        snapshots.clear();
        layers.clear();
        model.clear();

        model.loadCode(code.getText());

        altsList.getItems().addAll(model.getStateNames());

        for (SystemState s : model.getStates()) {
            Canvas c = createCanvas();
            layers.put(s, c);
            renderState(s);

            canvas.getChildren().add(c);
            // put all canvases on scenegraph
            // capture image
            // and build gallery
            WritableImage snapshot = c.snapshot(null, null);
            snapshots.put(s, snapshot);
            gallery.getChildren().add(createImageTile(snapshot));

            canvas.getChildren().clear();
        }

        altsList.getSelectionModel().select(0);

        appendToConsole("Executed: " + currentFile.toString() + "\n");
    }

    private void appendToConsole(String s) {
        String text = console.getText();
        String concat = text.concat(s);
        console.setText(concat);
    }

    private ImageView createImageTile(WritableImage image) {
        DropShadow ds = new DropShadow();
        ds.setOffsetY(3.0);
        ds.setOffsetX(3.0);
        ds.setColor(Color.GRAY);

        ImageView v = new ImageView(image);
        v.setFitHeight(200);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        v.setEffect(ds);
        return v;
    }
    
    private Group createLightTable(){
        Group lightTable = new Group();
        
        lightTable.setOnMousePressed(this::handleTilePressed);
        lightTable.setOnMouseDragged(this::handleTileDragged);
        lightTable.setOnMouseReleased(this::handleTileReleased);
        
        double x = 0;
        for(WritableImage img: snapshots.values()){
            ImageView tile = createImageTile(img);
            tile.relocate(x, 0);
            tile.setOnMousePressed(this::handleTileSelected);
            lightTable.getChildren().add(tile);
            
            x = x + 200 + 10;
        }
        
        
        return lightTable;
    }
    
    private void handleTileSelected(MouseEvent e){
        selectedTile = (ImageView) e.getSource();
    }
    
    private void handleTilePressed(MouseEvent e){
        if(selectedTile != null){
            ImageView v = selectedTile;
            moveContext.setInitialTranslateX(v.layoutXProperty().get());
            moveContext.setInitialTranslateY(v.layoutYProperty().get());
            moveContext.setMouseAnchorX(e.getX());
            moveContext.setMouseAnchorY(e.getY());
            System.out.println("tile pressed");
        }
    }
    
    private void handleTileDragged(MouseEvent e){
        if(selectedTile != null){
            ImageView v = selectedTile;
            v.setLayoutX(moveContext.getDragDestX(e.getX()));
            v.setLayoutY(moveContext.getDragDestY(e.getY()));
        }
    }
    
    private void handleTileReleased(MouseEvent e){
        System.out.println("tile released");
    }

    @FXML
    private void handleSave(ActionEvent event) {
        if (currentFile != null) {
            try (FileWriter writer = new FileWriter(currentFile)) {
                writer.write(code.getText());
                writer.flush();
                appendToConsole("Saved\n");
            } catch (IOException ex) {
                Logger.getLogger(FXMLViewerController.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    @FXML
    private void handleFileOpen(ActionEvent event) {
        try {
            FileChooser open = new FileChooser();
            File fileToOpen = open.showOpenDialog(root.getScene().getWindow());

            if (fileToOpen != null) {
                BufferedReader reader = new BufferedReader(new FileReader(fileToOpen));
                StringBuilder sb = new StringBuilder();
                String line = null;
                String newLine = "\n";
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                    sb.append(newLine);
                }
                code.setText(sb.toString().trim());
                currentFile = fileToOpen;

            }
        } catch (IOException ex) {

        }
    }

    @FXML
    private void handleSaveAs(ActionEvent event
    ) {
        FileChooser save = new FileChooser();
        save.setInitialFileName("Code.sro");
        save.setTitle("Save as .sro");
        File fileToSave = save.showSaveDialog(root.getScene().getWindow());

        if (fileToSave != null) {
            FileWriter writer = null;
            try {
                writer = new FileWriter(fileToSave);
                writer.write(code.getText());
                writer.flush();
            } catch (IOException ex) {
                Logger.getLogger(FXMLViewerController.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    writer.close();
                } catch (IOException ex) {
                    Logger.getLogger(FXMLViewerController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    
    @FXML
    private void layoutAsGrid(ActionEvent event) {
        lightTableScrollPane.setContent(gallery);
        
        gallery.getChildren().clear();
        for(WritableImage img: snapshots.values()){
            gallery.getChildren().add(createImageTile(img));
        }
    }

    @FXML
    private void handleFreeFormLayout(ActionEvent event) {
        if(lightTable.getChildren().isEmpty()){
            lightTable = createLightTable();
        }
        lightTableScrollPane.setContent(lightTable);
    }

    private void addEventHandlersToList() {
        altsList.getSelectionModel().getSelectedItems().addListener((ListChangeListener.Change<? extends String> c) -> {
            if (c.getList().size() > 1) {
                handleMultipleSelectedAltChange(c.getList());
            } else if (c.getList().size() == 1){
                handleSingleSelectedAltChange(c.getList().get(0));
            }
        });
    }

    public void handleSingleSelectedAltChange(String altName) {
        SystemState s = model.getState(altName);

        Canvas c = layers.get(s);
        ObservableList<Node> children = canvas.getChildren();
        children.clear();
        children.add(c);
    }

    public void handleMultipleSelectedAltChange(List<? extends String> alts) {
        ObservableList<Node> children = canvas.getChildren();
        children.clear();

        for (String s : alts) {
            Canvas c = renderState(model.getState(s));
            children.add(c);
        }
    }

    /**
     * Add the appropriate geometry to the canvas.
     *
     * @param s
     * @return
     */
    private Canvas renderState(SystemState s) {
        // evaluate the parametric system
        model.update(s);

        // look up the state's Canvas
        Canvas c = layers.get(s);

        // draw the geometry on the canvas
        updateCanvas(c.getDrawing());

        return c;
    }

    /**
     * Create a Canvas and setup the event handlers
     *
     * @return Canvas object with event handlers set
     */
    private Canvas createCanvas() {
        Canvas c = new Canvas();
        return c;
    }

    /**
     * Draw geometry on canvas
     *
     * @param canvas Group to add geometry to
     */
    public void updateCanvas(Group canvas) {
        // get geometry from parametric system
        canvas.getChildren().clear();

        // for each line in the model
        for (shiro.Node n : model.getNodesOfType("Line")) {
            // create a line object and render
            Line l = getLine(n);
            canvas.getChildren().add(l);
        }

        for (shiro.Node n : model.getNodesOfType("Rectangle")) {
            Rectangle r = getRect(n);
            canvas.getChildren().add(r);
        }

        for (shiro.Node n : model.getNodesOfType("Circle")) {
            Circle c = getCircle(n);
            canvas.getChildren().add(c);
        }
        
        for(shiro.Node n: model.getNodesOfType("Arc")){
            Arc ac = getArc(n);
            canvas.getChildren().add(ac);
        }

//        // for each point in the model
//        for (shiro.Node n : model.getNodesOfType("Point")) {
//            // if the node active, render it
//            if (n.isActive()) {
//                canvas.getChildren().add(getPoint(n));
//            }
//        }
    }

//    public Group getPoint(shiro.Node n) {
//        Port ePort = n.getSelectedEvaluatedPort();
//        Value point = ePort.getValueForIndex(0);
//        Point2D tPoint = (Point2D) point.getValue();
//
//        Group p = ui.createPoint(tPoint.getX(), tPoint.getY(), Color.BLACK);
//        p.setUserData(n.getFullName());
//        return p;
//    }
    
    /**
     * Create a Line to
     *
     * @param n
     * @return
     */
    public Line getLine(shiro.Node n) {
        Port ePort = n.getSelectedEvaluatedPort();
        Value line = ePort.getValueForIndex(0);

        Line lTemp = (Line) line.getValue();

        Line l = new Line();
        l.setStartX(lTemp.getStartX());
        l.setStartY(lTemp.getStartY());
        l.setEndX(lTemp.getEndX());
        l.setEndY(lTemp.getEndY());

        return l;
    }

    public Rectangle getRect(shiro.Node n) {
        Port ePort = n.getSelectedEvaluatedPort();
        Value rect = ePort.getValueForIndex(0);

        Rectangle rTemp = (Rectangle) rect.getValue();

        Rectangle r = new Rectangle();
        r.setX(rTemp.getX());
        r.setY(rTemp.getY());
        r.setWidth(rTemp.getWidth());
        r.setHeight(rTemp.getHeight());
        r.setStroke(rTemp.getStroke());
        r.setStrokeWidth(rTemp.getStrokeWidth());
        r.setFill(rTemp.getFill());

        return r;
    }

    public Circle getCircle(shiro.Node n) {
        Port ePort = n.getSelectedEvaluatedPort();
        Value circle = ePort.getValueForIndex(0);

        Circle cTemp = (Circle) circle.getValue();

        Circle c = new Circle();
        c.setRadius(cTemp.getRadius());
        c.setCenterX(cTemp.getCenterX());
        c.setCenterY(cTemp.getCenterY());
        c.setStroke(cTemp.getStroke());
        c.setStrokeWidth(cTemp.getStrokeWidth());
        c.setFill(cTemp.getFill());

        return c;
    }
    
    public Arc getArc(shiro.Node n) {
        Port ePort = n.getSelectedEvaluatedPort();
        Value arc = ePort.getValueForIndex(0);

        Arc cTemp = (Arc) arc.getValue();

        Arc c = new Arc();
        c.setRadiusX(cTemp.getRadiusX());
        c.setRadiusY(cTemp.getRadiusY());
        c.setCenterX(cTemp.getCenterX());
        c.setCenterY(cTemp.getCenterY());
        c.setStartAngle(cTemp.getStartAngle());
        c.setLength(cTemp.getLength());
        c.setStroke(cTemp.getStroke());
        c.setStrokeWidth(cTemp.getStrokeWidth());
        c.setFill(cTemp.getFill());
        c.setType(cTemp.getType());

        return c;
    }
}
