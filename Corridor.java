import java.nio.*;
import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.lang.Math;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.*;
import com.jogamp.common.nio.Buffers;
import org.joml.*;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

public class Corridor extends JFrame implements GLEventListener {
    // Window set up
    private GL4 gl;
    private GLCanvas glCanvas; // Initialize canvas

    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 600;
    private static final String WINDOW_TITLE = "Textured Corridor";
    private static final String VERTEX_SHADER_FILE = "corridor-vertex.glsl";
    private static final String FRAGMENT_SHADER_FILE = "corridor-fragment.glsl";

    // Shader
    private int renderingProgram; // Shader Id
    private int mv_matrixID;
    private int p_matrixID;

    // Matrix Management
    private Matrix4f viewMatrix; // Stores view matrix
    private Matrix4f perspectiveMatrix = new Matrix4f(); // Stores perspective matrix
    private float aspectRatio;

    // Model Matrices
    private Matrix4f modelViewMatrix = new Matrix4f(); // Stores model matrix

    // Initialize scratch buffer in order to pass matrices to the gpu/shaders
    private final FloatBuffer scratchBuffer = Buffers.newDirectFloatBuffer(16);

    private int[] vao = new int[1];
    private int[] vbo = new int[2];

    // Model Management
    private Matrix4f northHallModelMatrix = new Matrix4f();
    private Matrix4f eastHallModelMatrix = new Matrix4f();
    private Matrix4f southHallModelMatrix = new Matrix4f();
    private Matrix4f westHallModelMatrix = new Matrix4f();

    // Time Management
    private long startTime;
    private long timeElapsed; // In Milis

    // Hallway Calculations
    float distanceFromLast;
    float angleFromLast;
    Vector3f newLocation;
    Vector3f temp;

    // Camera Management
    private float cameraX, cameraY, cameraZ;

    // Corner locations 0-3 for each of the hallways
    Vector3f[] cornerLocations = { new Vector3f(-450, (float) (2 / 3 - 0.5), -450),
            new Vector3f(450, (float) (2 / 3 - 0.5), -450), new Vector3f(450, (float) (2 / 3 - 0.5), 450),
            new Vector3f(-450, (float) (2 / 3 - 0.5), 450) };

    // Hallway directions constant
    Vector3f[] directions = { new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0),
            new Vector3f(0, 0, -1) };

    private float startingPoint[] = { -450, (float) (2 / 3 - 0.5), -450 };

    // Corridor arguments
    private int stepCount;
    private float stepHeight;

    // Vectors to keep track of positioning and targeting of the camera
    private Vector3f target; // A point the camera is going towards.
    private Vector3f upVector; // Define up to make the look at matrix easier to calculate

    // Time related
    private int walkingPeriod; // The total time it takes to walk down a corridor
    private int turningPeriod; // The total time it takes to turn to the new corridor
    private int totalTimePerSide; // Sum of walking and turning periods (w+t)
    private boolean walkTurn; // Boolean to check weather or not to set new target

    private int textureOneID, textureTwoID, textureThreeID, textureFourID; // Texture Ids
    private String textureOneS, textureTwoS, textureThreeS, textureFourS; // Texture Strings

    /**
     * Main method for program. Process arguments and make call to
     * constructor.
     * 
     * @param args
     */
    public static void main(String[] args) {
        try {
            int numArgs = args.length;

            if (args.length < 5) {
                throw new Exception("Not enough arguments please enter at least 5 arguments");
            }

            Float walkingPeriod = Float.parseFloat(args[0]);
            Float turningPeriod = Float.parseFloat(args[1]);
            int stepCount = Integer.parseInt(args[2]);
            Float stepHeight = Float.parseFloat(args[3]);

            String textureOne;
            String textureTwo;
            String textureThree;
            String textureFour;

            switch (numArgs) {
                case 5:
                    textureOne = args[4];
                    textureTwo = textureOne;
                    textureThree = textureOne;
                    textureFour = textureOne;
                    break;
                case 6:
                    textureOne = args[4];
                    textureTwo = args[5];
                    textureThree = textureOne;
                    textureFour = textureTwo;
                    break;
                case 7:
                    textureOne = args[4];
                    textureTwo = args[5];
                    textureThree = textureOne;
                    textureFour = args[6];
                    break;
                case 8:
                    textureOne = args[4];
                    textureTwo = args[5];
                    textureThree = args[6];
                    textureFour = args[7];
                    break;
                default:
                    throw new Exception("Too many texture inputs\n");
            }

            new Corridor(walkingPeriod, turningPeriod, stepCount, stepHeight, textureOne, textureTwo, textureThree,
                    textureFour); // Change function call here
        } catch (Exception e) {
            System.out.println("Non readable input. Please try using the following format:\n" +
                    "Walking period (integer/full number greater than 0)\n" +
                    "Turning period (integer/full number greater than 0)\n" +
                    "Step Count (integer/full number greater than 0)\n" +
                    "Step Height (integer/full number greater than 0)\n" +
                    "(optional) Strings (1-4) indicating the names of texture files (PNGs or JPEGs)\n" +
                    e + "\n");
        }
    }

    /**
     * Constructor for program. Set initial window parameters
     * and begin animation
     */
    public Corridor(Float walkingPeriod, Float turningPeriod, int stepCount, Float stepHeight, String textureOne,
            String textureTwo, String textureThree, String textureFour) {
        this.walkingPeriod = (int) (walkingPeriod * 1000);
        this.turningPeriod = (int) (turningPeriod * 1000);
        this.stepCount = stepCount;
        this.stepHeight = stepHeight;

        this.textureOneS = textureOne;
        this.textureTwoS = textureTwo;
        this.textureThreeS = textureThree;
        this.textureFourS = textureFour;

        setTitle(WINDOW_TITLE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        glCanvas = new GLCanvas();
        glCanvas.addGLEventListener(this);
        this.add(glCanvas);
        this.setVisible(true);
        setLocationRelativeTo(null);

        Animator animator = new Animator(glCanvas);
        animator.start();
    }

    /*
     * Initialize matrices and load models as well as all other necessary
     * pre-computation.
     */
    @Override
    public void init(GLAutoDrawable arg0) {
        // Set up window
        this.gl = (GL4) GLContext.getCurrentGL();
        renderingProgram = Utils.createShaderProgram(VERTEX_SHADER_FILE, FRAGMENT_SHADER_FILE); // Ready the program.

        setDefaultCloseOperation(EXIT_ON_CLOSE); // Set shutdown condition on close

        // Load in textures
        this.textureOneID = loadTexture(textureOneS);
        gl.glBindTexture(GL_TEXTURE_2D, textureOneID);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        this.textureTwoID = loadTexture(textureTwoS);
        gl.glBindTexture(GL_TEXTURE_2D, textureTwoID);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        this.textureThreeID = loadTexture(textureThreeS);
        gl.glBindTexture(GL_TEXTURE_2D, textureThreeID);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        this.textureFourID = loadTexture(textureFourS);
        gl.glBindTexture(GL_TEXTURE_2D, textureFourID);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        // Camera Set Up:
        this.cameraX = startingPoint[0];
        this.cameraY = startingPoint[1];
        this.cameraZ = startingPoint[2];

        gl.glEnable(GL_CULL_FACE);
        initializeModels();

        // Initial Transition Matrices
        Matrix4f northHallTranslation = new Matrix4f().translate(0, 0, -450);
        Matrix4f eastHallTranslation = new Matrix4f().translate(-450, 0, 0);
        Matrix4f southHallTranslation = new Matrix4f().translate(0, 0, 450);
        Matrix4f wastHallTranslation = new Matrix4f().translate(450, 0, 0);

        // Initial Rotation Matrices
        Matrix4f northHallRotation = new Matrix4f().rotateY((float) Math.toRadians(0.0));
        Matrix4f eastHallRotation = new Matrix4f().rotateY((float) Math.toRadians(90.0));
        Matrix4f southHallRotation = new Matrix4f().rotateY((float) Math.toRadians(180.0));
        Matrix4f westHallRotation = new Matrix4f().rotateY((float) Math.toRadians(270.0));

        // Initial scaling Matrices
        Matrix4f northHallScale = new Matrix4f().scale(100);
        Matrix4f eastHallScale = new Matrix4f().scale(100);
        Matrix4f southHallScale = new Matrix4f().scale(100);
        Matrix4f westHallScale = new Matrix4f().scale(100);

        // Initial model matrices Matrix * Translate * Rotate * Scale
        northHallModelMatrix.mul(northHallTranslation).mul(northHallRotation).mul(northHallScale);
        eastHallModelMatrix.mul(eastHallTranslation).mul(eastHallRotation).mul(eastHallScale);
        southHallModelMatrix.mul(southHallTranslation).mul(southHallRotation).mul(southHallScale);
        westHallModelMatrix.mul(wastHallTranslation).mul(westHallRotation).mul(westHallScale);

        // Initialize Matrices
        // Calculate initial view matrix
        this.viewMatrix = new Matrix4f().setLookAt(this.cameraX, this.cameraY, cameraZ, 450f, 50f, -450f, 0, 1, 0);

        // Shader Id Locations
        this.mv_matrixID = gl.glGetUniformLocation(renderingProgram, "mv_matrix"); // Save model matrix id
        this.p_matrixID = gl.glGetUniformLocation(renderingProgram, "p_matrix"); // Save model matrix id

        // Initialize Z buffers
        this.gl.glEnable(GL_DEPTH_TEST);
        this.gl.glDepthFunc(GL_LEQUAL);

        // Initialize position vectors
        this.target = new Vector3f().set(500, ((float) 2 / 3 - 0.5), -450);
        this.upVector = new Vector3f().set(0, 1, 0);

        // Time related initializations
        this.startTime = System.currentTimeMillis();
        this.totalTimePerSide = turningPeriod + walkingPeriod;
        this.walkTurn = false;
        this.newLocation = new Vector3f();
        this.temp = new Vector3f();
    }

    /*
     * Prepare models and matrices to be drawn.
     */
    @Override
    public void display(GLAutoDrawable arg0) {
        // Clear screen and Z buffer
        this.gl.glClear(GL_COLOR_BUFFER_BIT); // clear screen
        this.gl.glClear(GL_DEPTH_BUFFER_BIT); // clear Z-buffer
        this.gl.glUseProgram(renderingProgram); // Shader Id to use
        this.gl.glClearColor(0f, 0f, 0f, 1f); // Black Background

        // Time calculations
        this.timeElapsed = (System.currentTimeMillis() - startTime) % (4 * totalTimePerSide);
        int Hallway = (int) (this.timeElapsed / this.totalTimePerSide);

        // Move the camera depending on where in the corridor cycle it is
        if (isWalking((int) timeElapsed)) {

            if (!walkTurn) {
                walkTurn = true;
            }

            distanceFromLast = 900 * (timeElapsed % totalTimePerSide) / walkingPeriod; // Distance from last

            directions[Hallway % 4].mul(distanceFromLast, temp);

            cornerLocations[Hallway % 4].add(temp, newLocation);

            // Create Bounce effect
            newLocation.y = (float) (((float) 2 / 3 - 0.5f)
                    + stepHeight * Math.abs(Math.sin(((float) (stepCount * Math.PI) / (900)) * distanceFromLast)));

            // Create new look at matrix
            this.viewMatrix.setLookAt(newLocation, this.target, upVector);
        } else {
            // Rotate the target 90 digress
            if (walkTurn) {
                target.rotateY((float) Math.toRadians(270));
                walkTurn = false;
            }

            // Calculate how much to rotate
            angleFromLast = (float) (timeElapsed % totalTimePerSide);
            angleFromLast -= (float) walkingPeriod;
            angleFromLast /= (float) this.turningPeriod;
            angleFromLast = 1 - angleFromLast;
            angleFromLast *= (float) Math.toRadians(90.0f);

            target.rotateY(angleFromLast, temp); // Save into temp

            // Create new look at matrix
            this.viewMatrix.setLookAt(cornerLocations[(Hallway + 1) % 4], target, upVector)
                    .rotateLocalY(-angleFromLast);
        }

        // Get translation
        this.viewMatrix.mul(northHallModelMatrix, modelViewMatrix);

        // Load in texture S and T values to the shader
        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
        this.gl.glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(1);

        // MODEL * MODEL VIEW
        // North corridor
        this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));
        this.gl.glUniformMatrix4fv(p_matrixID, 1, false, perspectiveMatrix.get(scratchBuffer));

        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(0);

        this.gl.glActiveTexture(GL_TEXTURE0);
        this.gl.glBindTexture(GL_TEXTURE_2D, textureOneID);

        this.gl.glDrawArrays(GL_TRIANGLES, 0, 36);

        // West corridor
        this.viewMatrix.mul(westHallModelMatrix, modelViewMatrix);

        this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));
        this.gl.glUniformMatrix4fv(p_matrixID, 1, false, perspectiveMatrix.get(scratchBuffer));

        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(0);

        this.gl.glBindTexture(GL_TEXTURE_2D, textureTwoID);

        this.gl.glDrawArrays(GL_TRIANGLES, 0, 36);

        // South corridor
        this.viewMatrix.mul(southHallModelMatrix, modelViewMatrix);

        this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));
        this.gl.glUniformMatrix4fv(p_matrixID, 1, false, perspectiveMatrix.get(scratchBuffer));

        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(0);

        this.gl.glActiveTexture(GL_TEXTURE0);
        this.gl.glBindTexture(GL_TEXTURE_2D, textureThreeID);

        this.gl.glDrawArrays(GL_TRIANGLES, 0, 36);

        // East corridor
        this.viewMatrix.mul(eastHallModelMatrix, modelViewMatrix);

        this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));
        this.gl.glUniformMatrix4fv(p_matrixID, 1, false, perspectiveMatrix.get(scratchBuffer));

        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(0);

        this.gl.glActiveTexture(GL_TEXTURE0);
        this.gl.glBindTexture(GL_TEXTURE_2D, textureFourID);

        this.gl.glDrawArrays(GL_TRIANGLES, 0, 36);
    }

    /**
     * Check to see if the camera is at a point where it should be waking or turning
     * 
     * @param time
     * @return
     */
    public boolean isWalking(int time) {
        return (time % totalTimePerSide) < walkingPeriod;
    }

    /*
     * Upon resize event change the perspective matrix to reflect the new aspect
     * ratio of the screen.
     */
    @Override
    public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {
        aspectRatio = (float) glCanvas.getWidth() / (float) glCanvas.getHeight(); // Get new aspect ratio
        // Set new perspective
        perspectiveMatrix.setPerspective((float) Math.toRadians(60.0f), aspectRatio, 0.1f, 10000.0f);
    }

    @Override
    public void dispose(GLAutoDrawable arg0) {
        // TODO Auto-generated method stub
    }

    /**
     * Initialize and load models into VBOs and S/T coordinates
     */
    private void initializeModels() {
        GL4 gl = (GL4) GLContext.getCurrentGL();
        float[] trapezoidSection = {
                // Top section:
                -4f, 0.5f, 0.5f, -5f, 0.5f, -0.5f, 4f, 0.5f, 0.5f,
                4f, 0.5f, 0.5f, -5f, 0.5f, -0.5f, 5f, 0.5f, -0.5f,
                // Bottom section:
                -5f, -0.5f, -0.5f, -4, -0.5f, 0.5f, 4f, -0.5f, 0.5f,
                -5f, -0.5f, -0.5f, 4f, -0.5f, 0.5f, 5f, -0.5f, -0.5f,
                // Long wall
                -5f, 0.5f, -0.5f, -5f, -0.5f, -0.5f, 5f, -0.5f, -0.5f,
                -5f, 0.5f, -0.5f, 5f, -0.5f, -0.5f, 5f, 0.5f, -0.5f,
                // Short wall
                -4f, -0.5f, 0.5f, -4f, 0.5f, 0.5f, 4f, -0.5f, 0.5f,
                4f, -0.5f, 0.5f, -4f, 0.5f, 0.5f, 4f, 0.5f, 0.5f
        };

        // Set up Vao
        gl.glGenVertexArrays(vao.length, vao, 0);
        gl.glBindVertexArray(vao[0]);
        gl.glGenBuffers(vbo.length, vbo, 0);

        // Load model into buffer location 0
        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        FloatBuffer vertBuf = Buffers.newDirectFloatBuffer(trapezoidSection);
        gl.glBufferData(GL_ARRAY_BUFFER, vertBuf.limit() * 4, vertBuf, GL_STATIC_DRAW);

        float[] STValues = {
                1, 1, 0f, 0f, 1f, 9f,
                1f, 9f, 0f, 0f, 0, 10f,
                // Bottom
                0f, 0f, 1, 1, 1f, 9f,
                0f, 0f, 1f, 9f, 0, 10f,
                // Long
                0f, 1f, 0f, 0f, 10f, 0f,
                0f, 1f, 10f, 0f, 10f, 1f,
                // Short
                0f, 0f, 0f, 1f, 8f, 0f,
                8f, 0f, 0f, 1f, 8f, 1f
        };

        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
        FloatBuffer textureBuffer = Buffers.newDirectFloatBuffer(STValues);
        gl.glBufferData(GL_ARRAY_BUFFER, textureBuffer.limit() * 4, textureBuffer, GL_STATIC_DRAW);
    }

    /**
     * Custom load texture to handel user texture input error with better user
     * friendly dialog
     * 
     * @param textureFileName
     * @return
     */
    public int loadTexture(String textureFileName) {
        GL4 gl = (GL4) GLContext.getCurrentGL();
        int finalTextureRef;
        Texture tex = null;
        try {
            tex = TextureIO.newTexture(new File(textureFileName), false);
        } catch (Exception e) {
            System.out.println(
                    "Sorry your textures did not load correctly. \nPlease make sure they are spelled and the texture \nfiles are in the correct specified location");
            super.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));

        }
        finalTextureRef = tex.getTextureObject();

        // building a mipmap and use anisotropic filtering
        gl.glBindTexture(GL_TEXTURE_2D, finalTextureRef);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        gl.glGenerateMipmap(GL_TEXTURE_2D);
        if (gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic")) {
            float anisoset[] = new float[1];
            gl.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, anisoset, 0);
            gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, anisoset[0]);
        }
        return finalTextureRef;
    }
}