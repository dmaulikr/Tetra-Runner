package com.erich.tetrarunner;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Erich on 12/8/2014.
 * Main game class
 */
public class GameActivity extends Activity implements GLSurfaceView.Renderer
{
    public enum GameState { START, PLAYING, WIN, LOSE }

    static final int POSITION_ATTRIBUTE_ID = 0;
    static final int TEXTURE_ATTRIBUTE_ID = 1;
    static final int NORMAL_ATTRIBUTE_ID = 2;
    static final int MAX_FRAMES_PER_SECOND = 60;
    static final int MAX_NODES_LOADED = 40;
    static final float GRAVITY = -9.8f;

    static final float TURN_POWER = 20.0f;
    static final float JUMP_POWER = 4.0f;
    static final float MAX_SIDE_VELOCITY = 4.0f;
    static final float MAX_VELOCITY = 15.0f;

    static final float SHIP_WIDTH = 0.75f;
    static final float SHIP_LENGTH = 0.75f;

    static int _mvmLoc, _viewLoc, _projLoc;
    static int _ambientLoc, _diffuseLoc, _specularLoc, _emissiveLoc;
    static int _shineLoc;
    static int _lightPosLoc;
    static int _normalMatrixLoc;
    static int _program = -1;
    static int _wordsProgram = -1;

    static int _textureSamplerLoc;
    static int _wordsMvmLoc, _wordsProjLoc;
    static int[] _textures = new int[1];

    static GameBoard _gameBoard;
    static String _gameName;
    static long _time;
    static long _timeStarted;
    static long _gameTime;


    boolean noMusic, noSound;
    SoundPool soundPool;
    int[] soundIds;
    MediaPlayer backgroundMusic;

    float _position, _velocity, _acceleration, _sideVelocity, _sideAcceleration, _verticalVelocity;
    float _shipPositionX, _shipPositionY, _positionOffset;
    boolean _onGround, _stabilize, _falling;

    //Screen size
    int _width, _height;

    float[] _projectionMatrix;
    static float[] _wordsProjectionMatrix;
    float[] _modelViewMatrix;

    float[] _cubePoints;
    float[] _rightTetrahedronPoints;
    Node[] _shipNodes;

    float[] eye = { 0.0f, 0.5f, 3.0f };
    float[] at = { 0.0f, 0.0f, 0.0f };

    float theta = 0.0f;

    int _trackSize;

    HashMap<Integer, PointF> _activePointers;
    int _countDown;
    int _coinsCollected;
    static GameState _gameState;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        //Retrieve the level data from the Intent data used to start activity (from MainActivity)
        _gameName = getIntent().getExtras().getString("gameName");
        noMusic = getIntent().getExtras().getBoolean("musicOff");
        noSound = getIntent().getExtras().getBoolean("soundOff");

        _activePointers = new HashMap<Integer, PointF>();

        GLSurfaceView surfaceView = new GLSurfaceView(this);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(this);

        if (!noSound) {
            soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
            soundIds = new int[10];
            soundIds[0] = soundPool.load(this, R.raw.bump_noise, 1);
            soundIds[1] = soundPool.load(this, R.raw.coin_noise, 1);
            soundIds[2] = soundPool.load(this, R.raw.countdown_noise, 1);
        }

        if (!noMusic) {
            backgroundMusic = MediaPlayer.create(GameActivity.this, R.raw.saturday);
            backgroundMusic.setLooping(true);
            backgroundMusic.setVolume(100,100);
            backgroundMusic.start();
        }


        _gameState = GameState.START;
        _countDown = 3000;

        setContentView(surfaceView);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (!noMusic) backgroundMusic.stop();
        GameData.saveGameRecords(this);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (!noMusic) backgroundMusic.stop();
        GameData.saveGameRecords(this);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig)
    {
        String vertexShaderSource = "";
        try {
            InputStream inputStream = getAssets().open("vertex-shader");
            InputStreamReader fileReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String read;
            while ((read = bufferedReader.readLine()) != null)
                vertexShaderSource += read + "\n";

        } catch (FileNotFoundException e)
        { e.printStackTrace(); }
        catch (IOException e) { e.printStackTrace(); }

        String fragmentShaderSource = "";
        try {
            InputStream inputStream = getAssets().open("fragment-shader");
            InputStreamReader fileReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String read;
            while ((read = bufferedReader.readLine()) != null)
                fragmentShaderSource += read + "\n";

        } catch (FileNotFoundException e)
        { e.printStackTrace(); }
        catch (IOException e) { e.printStackTrace(); }

        String texVertexShaderSource = "";
        try {
            InputStream inputStream = getAssets().open("tex_vertex-shader");
            InputStreamReader fileReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String read;
            while ((read = bufferedReader.readLine()) != null)
                texVertexShaderSource += read + "\n";

        } catch (FileNotFoundException e)
        { e.printStackTrace(); }
        catch (IOException e) { e.printStackTrace(); }

        String texFragmentShaderSource = "";
        try {
            InputStream inputStream = getAssets().open("tex_fragment-shader");
            InputStreamReader fileReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String read;
            while ((read = bufferedReader.readLine()) != null)
                texFragmentShaderSource += read + "\n";

        } catch (FileNotFoundException e)
        { e.printStackTrace(); }
        catch (IOException e) { e.printStackTrace(); }

        //Make sure these are valid
        int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShader, vertexShaderSource);
        GLES20.glCompileShader(vertexShader);
        String vertexShaderCompileLog = GLES20.glGetShaderInfoLog(vertexShader);
        Log.i("Vertex Shader Compile", vertexShaderCompileLog + "\n");

        int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShader, fragmentShaderSource);
        GLES20.glCompileShader(fragmentShader);
        String fragmentShaderCompileLog = GLES20.glGetShaderInfoLog(fragmentShader);
        Log.i("fragment Shader Compile", fragmentShaderCompileLog + "\n");

        int texVertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(texVertexShader, texVertexShaderSource);
        GLES20.glCompileShader(texVertexShader);
        vertexShaderCompileLog = GLES20.glGetShaderInfoLog(texVertexShader);
        Log.i("Texture Vertex Shader Compile", vertexShaderCompileLog + "\n");

        int texFragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(texFragmentShader, texFragmentShaderSource);
        GLES20.glCompileShader(texFragmentShader);
        fragmentShaderCompileLog = GLES20.glGetShaderInfoLog(texFragmentShader);
        Log.i("Texture fragment Shader Compile", fragmentShaderCompileLog + "\n");

        _program = GLES20.glCreateProgram();
        GLES20.glAttachShader(_program, vertexShader);
        GLES20.glAttachShader(_program, fragmentShader);

        _wordsProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(_wordsProgram, texVertexShader);
        GLES20.glAttachShader(_wordsProgram, texFragmentShader);

        GLES20.glBindAttribLocation(_program, POSITION_ATTRIBUTE_ID, "position");
        GLES20.glBindAttribLocation(_program, NORMAL_ATTRIBUTE_ID, "normal");

        GLES20.glBindAttribLocation(_wordsProgram, POSITION_ATTRIBUTE_ID, "position");
        GLES20.glBindAttribLocation(_wordsProgram, TEXTURE_ATTRIBUTE_ID, "texture");

        GLES20.glLinkProgram(_program);
        String programLinkLog = GLES20.glGetProgramInfoLog(_program);
        Log.i("Program Link", programLinkLog + "\n");

        GLES20.glLinkProgram(_wordsProgram);
        programLinkLog = GLES20.glGetProgramInfoLog(_wordsProgram);
        Log.i("Program Link", programLinkLog + "\n");

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        GLES20.glEnableVertexAttribArray(POSITION_ATTRIBUTE_ID);
        GLES20.glEnableVertexAttribArray(NORMAL_ATTRIBUTE_ID);
        GLES20.glEnableVertexAttribArray(TEXTURE_ATTRIBUTE_ID);

        //Uniform locations
        _viewLoc = GLES20.glGetUniformLocation(_program, "viewMatrix");
        _projLoc = GLES20.glGetUniformLocation(_program, "projectionMatrix");
        _ambientLoc = GLES20.glGetUniformLocation(_program, "ambientProduct");
        _diffuseLoc = GLES20.glGetUniformLocation(_program, "diffuseProduct");
        _specularLoc = GLES20.glGetUniformLocation(_program, "specularProduct");
        _emissiveLoc = GLES20.glGetUniformLocation(_program, "emissive");
        _shineLoc = GLES20.glGetUniformLocation(_program, "shine");
        _lightPosLoc = GLES20.glGetUniformLocation(_program, "lightPosition");
        _mvmLoc = GLES20.glGetUniformLocation(_program, "modelViewMatrix");
        _normalMatrixLoc = GLES20.glGetUniformLocation(_program, "normalMatrix");

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.nums);
        GLES20.glGenTextures(1, _textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _textures[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();

        _wordsMvmLoc = GLES20.glGetUniformLocation(_wordsProgram, "modelViewMatrix");
        _wordsProjLoc = GLES20.glGetUniformLocation(_wordsProgram, "projectionMatrix");
        _textureSamplerLoc = GLES20.glGetUniformLocation(_wordsProgram, "textureSampler");

        _modelViewMatrix = new float[16];
        _shipNodes = new Node[3];

//        _cubePoints = GeometryBuilder.getCube();
//        _rightTetrahedronPoints = GeometryBuilder.getRightTetrahedron();

        initializeBoard();
        _positionOffset = 3.0f;
        _position = 0.0f;
        _velocity = 1.0f;
        _sideVelocity = 0.0f;
        _verticalVelocity = 0.0f;
        _acceleration = 5.0f;
        _shipPositionX = 0.0f;
        _shipPositionY = 0.0f;
        _onGround = true;
        _coinsCollected = 0;
        _timeStarted = System.currentTimeMillis();
        _time = _timeStarted;
        playNoise(Noises.COUNTDOWN);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height)
    {
        GLES20.glViewport(0, 0, width, height);
        _width = width;
        _height = height;

        float ratio = (float)width/height;

        _projectionMatrix = new float[16];
        Matrix.perspectiveM(_projectionMatrix, 0, 90.0f, ratio, 0.01f, 30.0f);
        _wordsProjectionMatrix = _projectionMatrix.clone();
        Matrix.translateM(_projectionMatrix, 0, 0.0f, 0.0f, -3.0f);
        Matrix.translateM(_wordsProjectionMatrix, 0, 0.0f, 0.0f, -1.0f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        int pIndex = event.getActionIndex();
        int pId = event.getPointerId(pIndex);
        int maskedAction = event.getActionMasked();

        switch(maskedAction)
        {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                PointF p = new PointF();
                p.x = event.getX(pIndex);
                p.y = event.getY(pIndex);
                _activePointers.put(pId, p);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
               _activePointers.remove(pId);
        }

        return true;
    }

    private void handleButtonPress()
    {
        switch (_gameState)
        {
            case START:
                break;
            case PLAYING:
                boolean leftPressed = false;
                boolean rightPressed = false;
                boolean jumpPressed = false;
                _sideAcceleration = 0.0f;
                _stabilize = true;
                int removalKey = -1;
                for (Integer key : _activePointers.keySet())
                {
                    PointF p = _activePointers.get(key);
                    if (p == null)
                        continue;

                    float percentX = (p.x * 100.0f) / _width;
                    float percentY = (p.y * 100.0f) / _height;

                    //Pressing right or left will cancel the other direction
                    if (percentX < 20) {
                        leftPressed = true;
                        rightPressed = false;
                    } else if (percentX > 80) {
                        rightPressed = true;
                        leftPressed = false;
                    } else if (percentY > 50) {
                        jumpPressed = true;
                        removalKey = key;
                    }
                }

                if (removalKey != -1)
                    _activePointers.remove(removalKey);

                if (leftPressed) {
                    _sideAcceleration = -TURN_POWER;
                    _stabilize = false;
                }

                if (rightPressed) {
                    _sideAcceleration = TURN_POWER;
                    _stabilize = false;
                }

                if (jumpPressed) {
                    if (_onGround) {
                        _verticalVelocity = JUMP_POWER;
                        _onGround = false;
                    }
                }
                break;
            case WIN:
            case LOSE:
                leftPressed = false;
                rightPressed = false;
                for (Integer key : _activePointers.keySet())
                {
                    PointF p = _activePointers.get(key);
                    float percentX = (p.x * 100.0f) / _width;
                    //Pressing right or left will cancel the other direction
                    if (percentX < 50) {
                        leftPressed = true;
                    } else {
                        rightPressed = true;
                    }
                }
                if (leftPressed)
                    restartTrack();
                else if (rightPressed)
                    finish();
                break;
        }

    }

    private void calculateMovement()
    {
        //Calculate factor based on time elapsed since last call of this method
        //This keeps ship moving at a steady rate, regardless of rendering or processing time
        float factor  = updateTime() / 1000.0f;

        //First, calculate velocity(s)
        _velocity += (_acceleration * factor);
        if (_velocity > MAX_VELOCITY) _velocity = MAX_VELOCITY;

        //Calculate sideways movement
        if ((_sideVelocity > 0 && _sideAcceleration < 0) ||
            (_sideVelocity < 0 && _sideAcceleration > 0) ||
            (_stabilize))
        {
            _sideVelocity -= (_sideVelocity / 2);
            if (Math.abs(_sideVelocity) < 0.1)
                _sideVelocity = 0.0f;
        }
        else
            _sideVelocity += (_sideAcceleration * factor);

        if (_sideVelocity > MAX_SIDE_VELOCITY) _sideVelocity = MAX_SIDE_VELOCITY;
        if (_sideVelocity < -MAX_SIDE_VELOCITY) _sideVelocity = -MAX_SIDE_VELOCITY;


        _verticalVelocity += (GRAVITY * factor);

        collisionDetection(factor);

        //If low frame rate, position will skip over squares at high velocity
        float tempPosition = _position;
        float tempVelocity = _velocity * factor;
        while (tempVelocity > 1.0f)
        {
            tempVelocity -= 1.0f;
            _position += tempVelocity;
            collisionDetection(factor);
        }
        _position = tempPosition;

        //Forward motion
        _position += (_velocity * factor); //Z position of ship... think "where is ship on the track"

        //Horizontal Movement
        _shipPositionX += (_sideVelocity * factor);

        //Vertical movement
        _shipPositionY += (_verticalVelocity * factor);
        if (_shipPositionY < 0.0f && !_falling)
        {
            _shipPositionY = 0.0f;
            _verticalVelocity = 0.0f;
            _onGround = true;
        }
        else if (_falling) {
            _onGround = false;
            if (_shipPositionY < -3.0f)
            {
                //TODO: Die!
                death();
            }
        }

        theta += 1.0f;
    }

    private void collisionDetection(float factor)
    {
        //Calculate necessary offset for x-coordinate
        float coordCorrection = (0.5f * (_shipPositionX) / Math.abs((_shipPositionX)));

        //Get actual x-coordinate
        int realCoordinate = (int)(_shipPositionX + coordCorrection);
        int realLeftCoordinate = (int)(realCoordinate - 1.0f);
        int realRightCoordinate = (int)(realCoordinate + 1.0f);

        float horizontalShipPad = SHIP_WIDTH / 4.0f;
        horizontalShipPad *= Math.abs(_sideVelocity) > 0 ? ((_sideVelocity) / Math.abs((_sideVelocity))) : 0;
        //Calculate potential coordinates, based on horizontal velocity (for collision detection)
        int potentialRealCoordinate = (int)((_shipPositionX + horizontalShipPad + ((_sideVelocity + (_sideAcceleration * factor)) * factor)) + coordCorrection);

        int leftWingCoordinate = (int)((_shipPositionX - (SHIP_WIDTH / 3.0f) + coordCorrection));
        float potentialLeftWingCoordinate = (int)((_shipPositionX - (SHIP_WIDTH / 3.0f) + ((_sideVelocity + (_sideAcceleration * factor)) * factor)) + coordCorrection);
        int rightWingCoordinate = (int)((_shipPositionX + (SHIP_WIDTH / 3.0f) + coordCorrection));
        float potentialRightWingCoordinate = (int)((_shipPositionX + (SHIP_WIDTH / 3.0f) + ((_sideVelocity + (_sideAcceleration * factor)) * factor)) + coordCorrection);

        int realPosition = (int)(_position + _positionOffset);
        float forwardShipPad = SHIP_LENGTH / 2.0f;
        int potentialRealPosition = (int)((_position + _positionOffset) + forwardShipPad + ((_velocity + (_acceleration * factor)) * factor));

        boolean checkCorners;
        checkCorners = (potentialRealPosition != realPosition && (
                        potentialRealCoordinate != realCoordinate ||
                        (leftWingCoordinate != realCoordinate ^ rightWingCoordinate != realCoordinate)
                       ));

        //Get current and all adjacent Actors relative to the ship
        GameActor currentUpperSquare = null;
        GameActor forwardUpperSquare = null;
        GameActor leftUpperSquare    = null;
        GameActor rightUpperSquare   = null;
        GameActor currentLowerSquare = null;
        //GameActor forwardLowerSquare = null;
        GameActor leftForwardUpperSquare    = null;
        GameActor rightForwardUpperSquare   = null;
        if (realCoordinate >= -2 && realCoordinate <= 2) {
            currentUpperSquare = (realPosition >= _trackSize) ? null : _gameBoard.getBoard().get(realPosition).getUpperLayer()[realCoordinate + 2];
            currentLowerSquare = (realPosition >= _trackSize) ? null : _gameBoard.getBoard().get(realPosition).getLowerLayer()[realCoordinate + 2];
            forwardUpperSquare = (realPosition >= _trackSize-1) ? null : _gameBoard.getBoard().get(realPosition + 1).getUpperLayer()[realCoordinate + 2];
            //forwardLowerSquare = (realPosition > _trackSize) ? null : _gameBoard.getBoard().get(realPosition + 1).getLowerLayer()[realCoordinate + 2];
            if (realLeftCoordinate >= -2) {
                leftUpperSquare = (realPosition >= _trackSize) ? null : _gameBoard.getBoard().get(realPosition).getUpperLayer()[realLeftCoordinate + 2];
                leftForwardUpperSquare = (realPosition >= _trackSize-1) ? null : _gameBoard.getBoard().get(realPosition + 1).getUpperLayer()[realLeftCoordinate + 2];
            }
            if (realRightCoordinate <= 2) {
                rightUpperSquare = (realPosition >= _trackSize) ? null : _gameBoard.getBoard().get(realPosition).getUpperLayer()[realRightCoordinate + 2];
                rightForwardUpperSquare = (realPosition >= _trackSize-1) ? null : _gameBoard.getBoard().get(realPosition + 1).getUpperLayer()[realRightCoordinate + 2];
            }
        }

        //Coin case
        if (currentUpperSquare != null && _shipPositionY >= 0.0f)
        {
            GameActor.ActorType type = currentUpperSquare.getType();
            if (type == GameActor.ActorType.coin)
            {
                //TODO: Collect a coin
                playNoise(Noises.COIN);
                _coinsCollected++;
                _gameBoard.getBoard().get((int)(_position + _positionOffset)).getUpperLayer()[realCoordinate+2] = new GameActor(GameActor.ActorType.empty);
            }
        }

        //Barrier case
        if (forwardUpperSquare != null && _shipPositionY >= -0.1f)
        {
            GameActor.ActorType type = forwardUpperSquare.getType();
            if (type == GameActor.ActorType.barrier
                    && potentialRealPosition == realPosition + 1)
            {
                if (_velocity > 4.0f)
                    playNoise(Noises.BUMP);
                _velocity /= -5.0f;
            }
        }
        if (leftUpperSquare != null && _shipPositionY >= 0.0f)
        {
            GameActor.ActorType type = leftUpperSquare.getType();
            if (type == GameActor.ActorType.barrier
                    && (potentialRealCoordinate == realLeftCoordinate || potentialLeftWingCoordinate == realLeftCoordinate))
            {
                //TODO: Collide with block
                if (_sideVelocity < 0.0f) {
                    _sideVelocity = 0.0f;
                    potentialRealCoordinate = realCoordinate;
                }
            }
        }
        if (rightUpperSquare != null && _shipPositionY >= 0.0f)
        {
            GameActor.ActorType type = rightUpperSquare.getType();
            if (type == GameActor.ActorType.barrier
                    && (potentialRealCoordinate == realRightCoordinate || potentialRightWingCoordinate == realRightCoordinate))
            {
                //TODO: Collide with block
                if (_sideVelocity > 0.0f) {
                    _sideVelocity = 0.0f;
                    potentialRealCoordinate = realCoordinate;
                }
            }
        }
        if (checkCorners)
        {
            if (leftForwardUpperSquare != null && _shipPositionY >= 0.0f)
            {
                GameActor.ActorType type = leftForwardUpperSquare.getType();
                if (type == GameActor.ActorType.barrier && potentialRealPosition == realPosition + 1)
                {
                    if (potentialLeftWingCoordinate == realLeftCoordinate) {
                        if (_sideVelocity < 0.0f) {
                            _sideVelocity = 0.0f;
                        }
                        if (potentialRealCoordinate == realLeftCoordinate || leftWingCoordinate == realLeftCoordinate)
                        {
                            //TODO: Collide with block
                            if (_velocity > 4.0f)
                                playNoise(Noises.BUMP);
                            _velocity /= -5.0f;
                        }
                    }
                }
            }
            if (rightForwardUpperSquare != null && _shipPositionY >= 0.0f)
            {
                GameActor.ActorType type = rightForwardUpperSquare.getType();
                if (type == GameActor.ActorType.barrier && potentialRealPosition == realPosition + 1)
                {
                    if (potentialRightWingCoordinate == realRightCoordinate) {
                        if (_sideVelocity < 0.0f) {
                            _sideVelocity = 0.0f;
                        }
                        if (potentialRealCoordinate == realRightCoordinate || rightWingCoordinate == realRightCoordinate)
                        {
                            //TODO: Collide with block
                            if (_velocity > 4.0f)
                                playNoise(Noises.BUMP);
                            _velocity /= -5.0f;
                        }
                    }
                }
            }
        }

        //Hole case
        if (currentLowerSquare != null)
        {
            GameActor.ActorType type = currentLowerSquare.getType();
            if (type == GameActor.ActorType.floor && _shipPositionY >= -0.1f)
            {
                _falling = false;
            }
            else if (type == GameActor.ActorType.empty || _shipPositionY < -0.1f)
                _falling = true;
        }
        else
            _falling = true;
    }

    private void death()
    {
        _gameState = GameState.LOSE;
        clearButtonPresses();
    }

    private void restartTrack()
    {
        initializeBoard();
        _coinsCollected = 0;
        _position = 0.0f;
        _velocity = 0.0f;
        _verticalVelocity = 0.0f;
        _shipPositionX = 0.0f;
        _shipPositionY = 0.0f;
        _sideVelocity = 0.0f;
        _falling = false;
        _onGround = true;
        _countDown = 3000;
        _timeStarted = System.currentTimeMillis();
        _time = _timeStarted;
        _gameState = GameState.START;
        playNoise(Noises.COUNTDOWN);
    }

    /**
     *  Updates the time tracker based on the System.currentTimeMillis() call
     * @return number of milliseconds that have elapsed since last call.
     */
    private float updateTime()
    {
        long currentTime = System.currentTimeMillis();
        long timeElapsed = currentTime - _time;
        _time = currentTime;
        _gameTime = _time - _timeStarted;
        if (_gameState == GameState.START)
            _timeStarted = currentTime;
        return timeElapsed;
    }

    private void clearButtonPresses()
    {
        _activePointers.clear();
    }

    @Override
    public void onDrawFrame(GL10 gl10)
    {
        handleButtonPress();
        GameRecord record = GameData.getGameRecord(_gameBoard.boardName);
        switch (_gameState)
        {
            case START:
                _countDown -= updateTime();
                if (_countDown <= 0)
                {
                    if (record != null)
                    {
                        record.incrementNumTimesPlayed();
                    }
                    _gameState = GameState.PLAYING;
                }
                break;
            case PLAYING:
                if (_position + _positionOffset > _trackSize) {
                    _gameState = GameState.WIN;
                    clearButtonPresses();
                }
                else
                    calculateMovement();
                break;
            case WIN:
                if (record != null)
                {
                    if (record.getHighCoins() < _coinsCollected)
                        record.setHighCoins(_coinsCollected);
                    if (record.getBestTime() > _gameTime)
                        record.setBestTime(_gameTime);
                }
                break;
            case LOSE:
                break;
        }

        GLES20.glUseProgram(_program);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        Matrix.setLookAtM(_modelViewMatrix, 0, eye[0], eye[1], eye[2], at[0], at[1], at[2], 0.0f, 1.0f, 0.0f);
        GLES20.glUniformMatrix4fv(_viewLoc, 1, false, _modelViewMatrix, 0);
        GLES20.glUniformMatrix4fv(_projLoc, 1, false, _projectionMatrix, 0);

        float[] lightPosition =
        {
                -_shipPositionX, -_shipPositionY + 2.0f, (_position % 10) - 20.0f, 1.0f,
                -_shipPositionX, -_shipPositionY + 2.0f, (_position % 10) - 10.0f, 1.0f,
                -_shipPositionX, -_shipPositionY + 2.0f, (_position % 10)        , 1.0f,
                -_shipPositionX, -_shipPositionY + 2.0f, (_position % 10) + 10.0f, 1.0f,
        };
        GLES20.glUniform4fv(_lightPosLoc, 4, lightPosition, 0);

        float[] bodyModel = _modelViewMatrix.clone();
        Matrix.translateM(_modelViewMatrix, 0, -_shipPositionX, -_shipPositionY, 0.0f);

        float[] shipAmbient = {0.1f, 0.1f, 0.3f, 1.0f};
        float[] shipDiffuse = {0.2f, 0.3f, 0.5f, 1.0f};
        float[] shipSpecular = {0.6f, 0.6f, 0.6f, 1.0f};

        //Move and draw ship
        Matrix.translateM(bodyModel, 0, 0.0f, 0.0f, 4.0f);
        Matrix.rotateM(bodyModel, 0, (3.0f * -_sideVelocity), 0.0f, 0.0f, 1.0f);
        Matrix.rotateM(bodyModel, 0, (2.0f * _verticalVelocity), 1.0f, 0.0f, 0.0f);
        //Body of ship
        float[] instanceMatrix = bodyModel.clone();
        Matrix.translateM(instanceMatrix, 0, 0.0f, -0.05f, 0.3f);
        Matrix.scaleM(instanceMatrix, 0, 0.15f, 0.05f, 0.35f);
        Node shape = new Node(instanceMatrix);
        shape.setColor(shipAmbient, shipDiffuse, shipSpecular);
        shape.setPoints(GeometryBuilder.getCube());
        shape.setShine(2.0f);
        _shipNodes[0] = shape;
        //Right Wing
        instanceMatrix = bodyModel.clone();
        Matrix.translateM(instanceMatrix, 0, 0.25f, 0.0f, 0.0f);
        Matrix.scaleM(instanceMatrix, 0, 0.35f, 0.15f, 1.0f);
        shape = new Node(instanceMatrix);
        shape.setColor(shipAmbient, shipDiffuse, shipSpecular);
        shape.setPoints(GeometryBuilder.getRightTetrahedron());
        shape.setShine(2.0f);
        _shipNodes[1] = shape;
        //Left Wing
        instanceMatrix = bodyModel.clone();
        Matrix.translateM(instanceMatrix, 0, -0.25f, 0.0f, 0.0f);
        Matrix.scaleM(instanceMatrix, 0, 0.35f, 0.15f, 1.0f);
        Matrix.rotateM(instanceMatrix, 0, 90, 0.0f, 0.0f, 1.0f);
        shape = new Node(instanceMatrix);
        shape.setColor(shipAmbient, shipDiffuse, shipSpecular);
        shape.setPoints(GeometryBuilder.getRightTetrahedron());
        shape.setShine(2.0f);
        _shipNodes[2] = shape;


        traversal(_position);
        for (int i = 0; i < 3; i++)
            _shipNodes[i].render();


        GLES20.glUseProgram(_wordsProgram);
        drawText(_timeStarted, _time);

    }

    private void drawText(long timeStart, long currTime)
    {
        int mins = (int)Math.floor((currTime - timeStart) / 60000) % 60;
        int secs = (int)Math.floor((currTime - timeStart) / 1000) % 60;
        int hundredths = (int)Math.floor((currTime - timeStart) / 10) % 100;

        String sCoins = (_coinsCollected < 100) ? (_coinsCollected < 10) ? "00" + _coinsCollected : "0" + _coinsCollected : ""+_coinsCollected;
        String sMins = (mins < 10) ? "0" + mins : ""+mins;
        String sSecs = (secs < 10) ? "0" + secs : ""+secs;
        String sHuns = (hundredths < 10) ? "0" + hundredths : ""+hundredths;

        NumberGraphic[] display = new NumberGraphic[10];
        display[0] = new NumberGraphic(-0.45f, 0.9f, ':', 0.05f); //Triangle figure
        display[1] = new NumberGraphic(-0.4f, 0.9f, sCoins.charAt(0), 0.05f);
        display[2] = new NumberGraphic(-0.35f, 0.9f, sCoins.charAt(1), 0.05f);
        display[3] = new NumberGraphic(-0.3f, 0.9f, sCoins.charAt(2), 0.05f);

        display[4] = new NumberGraphic(0.1f, 0.9f, sMins.charAt(0), 0.05f);
        display[5] = new NumberGraphic(0.15f, 0.9f, sMins.charAt(1), 0.05f);

        display[6] = new NumberGraphic(0.225f, 0.9f, sSecs.charAt(0), 0.05f);
        display[7] = new NumberGraphic(0.275f, 0.9f, sSecs.charAt(1), 0.05f);

        display[8] = new NumberGraphic(0.35f, 0.9f, sHuns.charAt(0), 0.05f);
        display[9] = new NumberGraphic(0.40f, 0.9f, sHuns.charAt(1), 0.05f);

        for (int i = 0; i < 10; i++)
        {
            display[i].render();
        }

        StringGraphic timeGraphic = new StringGraphic(0.0f, 0.9f, 0, 0.05f);
        timeGraphic.render();

        if (_gameState == GameState.START)
        {
            NumberGraphic count = new NumberGraphic(0.0f, 0.0f, (char)((_countDown / 1000)+49), 0.25f);
            count.render();
        }

        if (_gameState == GameState.LOSE)
        {
            StringGraphic[] loseScreen = new StringGraphic[3];
            loseScreen[0] = new StringGraphic(0.0f, 0.0f, 1, 0.125f);
            loseScreen[1] = new StringGraphic(-0.3f, -0.3f, 3, 0.05f);
            loseScreen[2] = new StringGraphic(0.3f, -0.3f, 4, 0.05f);

            for (int i=0; i<3; i++)
                loseScreen[i].render();
        }

        if (_gameState == GameState.WIN)
        {
            StringGraphic[] winScreen = new StringGraphic[3];
            winScreen[0] = new StringGraphic(0.0f, 0.0f, 2, 0.125f);
            winScreen[1] = new StringGraphic(-0.3f, -0.3f, 3, 0.05f);
            winScreen[2] = new StringGraphic(0.3f, -0.3f, 4, 0.05f);

            for (int i=0; i<3; i++)
                winScreen[i].render();
        }
    }

    public static float[] getNormalMatrix(float[] mvm)
    {
        float[] normalMatrix = new float[mvm.length];
        Matrix.setIdentityM(normalMatrix, 0);
        Matrix.invertM(normalMatrix, 0, mvm, 0);
        float[] normalMatrixTransposed = new float[mvm.length];
        Matrix.transposeM(normalMatrixTransposed, 0, normalMatrix, 0);
        return normalMatrixTransposed;
    }

    private void initializeBoard()
    {
        _gameBoard = new GameBoard(GameData.getGameBoard(_gameName));
        _trackSize = _gameBoard.getSize();
    }

    private void traversal(float position)
    {
        int pos = (int)position;
        if (pos > _gameBoard.getSize() - 1 || pos - _position > MAX_NODES_LOADED)
            return;

        for (int i = 0; i < GameBoard.BOARD_WIDTH; i++)
        {
            GameActor actor = _gameBoard.getBoard().get(pos).getUpperLayer()[i];

            if (actor.getType() == GameActor.ActorType.empty)
                continue;

            float[] instanceMatrix;
            //Traversal
            instanceMatrix = _modelViewMatrix.clone();
            Matrix.scaleM(instanceMatrix, 0, 1.0f, 1.0f, 1.0f);
            Matrix.translateM(instanceMatrix, 0, (-2.0f + i), 0.0f, (6.0f - pos + _position));

            if (actor.getType() == GameActor.ActorType.coin)
            {
                Matrix.rotateM(instanceMatrix, 0, theta*8.0f, 0.0f, 1.0f, 0.0f);
                Matrix.rotateM(instanceMatrix, 0, theta*2.0f, 1.0f, 0.0f, 0.0f);
                Matrix.scaleM(instanceMatrix, 0, 0.5f, 0.5f, 0.5f);
            }

            Node shape = new Node(instanceMatrix);
            shape.setPoints(actor.getPoints());
            shape.setColor(actor.ambient, actor.diffuse, actor.specular);
            shape.setShine(actor.shine);
            shape.render();
        }

        for (int i = 0; i < GameBoard.BOARD_WIDTH; i++)
        {
            GameActor actor = _gameBoard.getBoard().get(pos).getLowerLayer()[i];

            if (actor.getType() == GameActor.ActorType.empty)
                continue;

            float[] instanceMatrix;
            //Traversal
            instanceMatrix = _modelViewMatrix.clone();
            Matrix.scaleM(instanceMatrix, 0, 1.0f, 1.0f, 1.0f);
            Matrix.translateM(instanceMatrix, 0, (-2.0f + i), -0.5f, (6.0f - pos + _position));

            if (actor.getType() == GameActor.ActorType.pit)
            {
                Matrix.scaleM(instanceMatrix, 0, 1.0f, 1.0f, 1.0f);
                Matrix.translateM(instanceMatrix, 0, 0.0f, -1.0f, 0.0f);
            }

            Node shape = new Node(instanceMatrix);
            shape.setPoints(actor.getPoints());
            shape.setColor(actor.ambient, actor.diffuse, actor.specular);
            shape.setShine(actor.shine);
            shape.render();
        }

        position += 1.0f;
        traversal(position);
    }

    private enum Noises { BUMP, COUNTDOWN, COIN }
    private void playNoise(Noises type)
    {
        if (noSound)
            return;

        switch (type)
        {
            case BUMP:
                soundPool.play(soundIds[0], 1, 1, 1, 0, 1.0f);
                break;
            case COIN:
                soundPool.play(soundIds[1], 1, 1, 1, 0, 1.0f);
                break;
            case COUNTDOWN:
                soundPool.play(soundIds[2], 1, 1, 1, 0, 1.0f);
                break;
        }
    }
}
