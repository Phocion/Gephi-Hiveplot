package edu.drexel.ischool.gephi;

import edu.drexel.ischool.gephi.NodeComparator.CompareType;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.Node;
import org.gephi.layout.plugin.AbstractLayout;
import org.gephi.layout.plugin.ForceVectorNodeLayoutData;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutProperty;
import org.gephi.statistics.plugin.Degree;
import org.gephi.statistics.plugin.GraphDistance;
import org.openide.util.NbBundle;

/**
 * The main HivePlot (http://hiveplot.net) layout renderer.
 * @author Tim Schultz - iSchool at Drexel University (http://ischool.drexel.edu/).
 */
public class Hiveplot extends AbstractLayout implements Layout 
{
    private float canvasArea;           // Set boundary for node placement.
    private int numAxes;                // Total number of axes.
    private String axesOrderProperty;   // Propert which dictates node axis.
    private String nodeOrderProperty;   // Property which dictates node order.
    protected Graph graph;              // The graph being laid out.

    // Debugging
    private final Logger LOGGER = Logger.getLogger(getClass().getName());

    /**
     * Constructor. Fancy!
     * @param layoutBuilder 
     */
    public Hiveplot(LayoutBuilder layoutBuilder)
    {
        super(layoutBuilder);
        this.canvasArea = 50000;
        this.numAxes = 3;
        this.axesOrderProperty = GraphDistance.BETWEENNESS;
        this.nodeOrderProperty = Degree.DEGREE;
        LOGGER.setLevel(Level.INFO);
    }
    
    @Override
    public void initAlgo()
    {
        this.graph = graphModel.getGraphVisible();
        for (Node n : graph.getNodes())
            n.getNodeData().setLayoutData(new ForceVectorNodeLayoutData());
    }
    
    @Override
    public void goAlgo() 
    {
        this.graph.readLock();
        boolean axisSort = this.nodeOrderProperty != null && !this.nodeOrderProperty.equals("");
        double degree = 360/this.numAxes;                       // a' between axes
        List<Node[]> sortNodes = generateAxes(axisSort, true);  // Axes 
        Point2D.Float[] p = new Point2D.Float[this.numAxes];    // Max points for each axis
        Point2D.Float[] z = new Point2D.Float[this.numAxes];    // Next node position to draw
        Point2D.Float[] d = new Point2D.Float[this.numAxes];    // Avg. position delta
        
        // Calculate outer boundary points for all axes - centered on (0,0)
        for(int x = 0; x < this.numAxes; x++) {
            p[x] = new Point2D.Float(StrictMath.round(this.canvasArea * (Math.sin((Math.toDegrees(degree * (x + 1)))))),
                                     StrictMath.round(this.canvasArea * (Math.cos((Math.toDegrees(degree * (x + 1)))))));
            d[x] = new Point2D.Float(Math.abs(p[x].x/(sortNodes.get(x).length)), Math.abs(p[x].y/(sortNodes.get(x).length))); // TODO
        }
        z = p;

        for(Node[] groups : sortNodes)
        {
            for (Node n : groups)
            {
                int pos = sortNodes.indexOf(groups);
                z[pos] = new Point2D.Float((z[pos].x - (z[pos].x > 0 ? d[pos].x : -d[pos].x)),
                                           (z[pos].y - (z[pos].y > 0 ? d[pos].y : -d[pos].y)));
                n.getNodeData().setX(z[pos].x);
                n.getNodeData().setY(z[pos].y);
                //n.getNodeData().setLabel(Integer.toString(pos) + ": " + " = (" + n.getNodeData().x() + "," + n.getNodeData().y() + ")");
            }
        }

        this.graph.readUnlock();
    }

    /**
     * 
     */
    @Override
    public void endAlgo() 
    {
        for (Node n : graph.getNodes())
            n.getNodeData().setLayoutData(null);
    }
    
    @Override
    public boolean canAlgo() 
    {
        return true;
    }
    

    /**
     * 
     * @return 
     */
    @Override
    public LayoutProperty[] getProperties() 
    {
        List<LayoutProperty> properties = new ArrayList<LayoutProperty>();
        final String HIVEPLOT  = "Hiveplot Layout";

        try 
        {
            properties.add(LayoutProperty.createProperty(
                    this, Float.class,
                    NbBundle.getMessage(Hiveplot.class, "hiveplot.area.name"),
                    HIVEPLOT,
                    "hiveplot.area.name",
                    NbBundle.getMessage(Hiveplot.class, "hiveplot.area.desc"),
                    "getCanvasArea", "setCanvasArea"));
            properties.add(LayoutProperty.createProperty(
                    this, Integer.class,
                    NbBundle.getMessage(Hiveplot.class, "hiveplot.numAxes.name"),
                    HIVEPLOT,
                    "hiveplot.numAxes.name",
                    NbBundle.getMessage(Hiveplot.class, "hiveplot.numAxes.desc"),
                    "getNumAxes", "setNumAxes"));
            properties.add(LayoutProperty.createProperty(
                    this, String.class,
                    NbBundle.getMessage(Hiveplot.class, "hiveplot.axesOrderProperty.name"),
                    HIVEPLOT,
                    "hiveplot.axesOrderProperty.name",
                    NbBundle.getMessage(Hiveplot.class, "hiveplot.axesOrderProperty.desc"),
                    "getAxesOrderProperty", "setAxesOrderProperty"));
            properties.add(LayoutProperty.createProperty(
                    this, String.class,
                    NbBundle.getMessage(Hiveplot.class, "hiveplot.nodeOrderProperty.name"),
                    HIVEPLOT,
                    "hiveplot.nodeOrderProperty.name",
                    NbBundle.getMessage(Hiveplot.class, "hiveplot.nodeOrderProperty.desc"),
                    "getNodeOrderProperty", "setNodeOrderProperty"));
        } 
        catch (MissingResourceException e)
        {
            e.printStackTrace();
        }
        catch (NoSuchMethodException e)
        {
            e.printStackTrace();
        }

        return properties.toArray(new LayoutProperty[0]);
    }

    /**
     * Resets the value of the layout attributes.
     */
    @Override
    public void resetPropertiesValues()
    {
        this.canvasArea = 50000;
        this.axesOrderProperty = GraphDistance.BETWEENNESS;
        this.nodeOrderProperty = Degree.DEGREE;
        this.numAxes = 3;
    }

    // Calculate axis boundaries
    
    /**
     * 
     * @return 
     */
    private List<Node[]> generateAxes(boolean sortNodesOnAxis, boolean asc)
    {
        ArrayList<Node[]> nodeGroups = new ArrayList<Node[]>();
        Node[] n = this.graph.getNodes().toArray();
        Arrays.sort(n, new NodeComparator(graph, n, CompareType.ATTRIBUTE, this.axesOrderProperty, true));
        int[] order = findBins(n);
        int lowerBound = 0;
        int upperBound = order[0];
        
        for(int bin = 0; bin < order.length; bin++)
        {
            //LOGGER.log(Level.INFO, "+++++ Bin{0} = {1},{2}", new Object[]{bin, lowerBound, (upperBound)});
            nodeGroups.add((lowerBound != upperBound) ? Arrays.copyOfRange(n, lowerBound, upperBound) : Arrays.copyOf(n, lowerBound));
            if(bin < (order.length - 1))
            {
                lowerBound = upperBound;
                upperBound = lowerBound + order[bin + 1];
            }
        }
        
        // If the user selects sorting nodes along axes
        if(sortNodesOnAxis)
        {
            ArrayList<Node[]> nodeGroupsX = new ArrayList<Node[]>();
            for(Node[] nz : nodeGroups)
            {
                Arrays.sort(nz, new NodeComparator(graph, nz, CompareType.ATTRIBUTE, this.nodeOrderProperty, asc));
                nodeGroupsX.add(nz);
            }
            return nodeGroupsX;
        }
        else
            return nodeGroups;
    }
    
    /**
     * Generates an array of integers which represent bin cut-offs for sorted array.
     * @param nodes
     * @return 
     */
    private int[] findBins(Node[] nodes)
    {
        int totalBins = this.numAxes;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int[] bins = new int[totalBins];

        for(Node n : nodes)
        {
            double value = (Double) n.getAttributes().getValue(this.axesOrderProperty);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        double binSize = (max - min) / totalBins;
        for (Node n : nodes)
        {
            double value = (Double) n.getAttributes().getValue(this.axesOrderProperty);
            int binIndex = 0;
            if (binSize > 0.0)
            {
                binIndex = (int)((value - min) / binSize);
                if (binIndex == totalBins)
                {
                    binIndex--;
                }
            }
            bins[binIndex]++;
        }
        for(int x = 0; x < totalBins; x++)
            LOGGER.log(Level.INFO, "****** Bin{0} = {1}", new Object[]{x, bins[x]});

        return(bins);
    }

    // Accessors 

    /**
     * 
     * @return 
     */
    public int getNumAxes()
    {
     return this.numAxes;   
    }
    
    /**
     * 
     * @param numAxes 
     */
    public void setNumAxes(Integer numAxes)
    {
        this.numAxes = numAxes;
    }

    /**
     * 
     * @param axesOrderProperty
     * @return 
     */
    public String getAxesOrderProperty()
    {
        return this.axesOrderProperty;
    }
    
    /**
     * 
     * @param axesOrderProperty 
     */
    public void setAxesOrderProperty(String axesOrderProperty)
    {
        this.axesOrderProperty = axesOrderProperty;
    }
    
    /**
     * 
     * @return 
     */
    public String getNodeOrderProperty()
    {
        return this.nodeOrderProperty;
    }
    
    /**
     * 
     * @param nodeOrderProperty 
     */
    public void setNodeOrderProperty(String nodeOrderProperty)
    {
        this.nodeOrderProperty = nodeOrderProperty;
    }
    
    /**
     * 
     * @return 
     */
    public float getCanvasArea()
    {
        return this.canvasArea;
    }
    
    /**
     * 
     * @param area 
     */
    public void setCanvasArea(Float canvasArea)
    {
        this.canvasArea = canvasArea;
    }
}