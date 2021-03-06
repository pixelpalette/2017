package org.usfirst.frc.team339.Vision;

import java.util.Comparator;
import java.util.Vector;
import com.ni.vision.NIVision;
import com.ni.vision.NIVision.Image;
import org.usfirst.frc.team339.HardwareInterfaces.KilroyCamera;
import org.usfirst.frc.team339.Vision.operators.ConvexHullOperator;
import org.usfirst.frc.team339.Vision.operators.HSLColorThresholdOperator;
import org.usfirst.frc.team339.Vision.operators.LoadColorImageJPEGOperator;
import org.usfirst.frc.team339.Vision.operators.RemoveSmallObjectsOperator;
import org.usfirst.frc.team339.Vision.operators.SaveBinaryImagePNGOperator;
import org.usfirst.frc.team339.Vision.operators.VisionOperatorInterface;
import edu.wpi.first.wpilibj.image.NIVisionException;


// TODO prints under debug switch
/**
 * A class to capture and process images. Provides information on the pictures
 * it captures and the blobs in it.
 * Make sure you give it a camera and a Vision script when you create it!
 * 
 * @author Noah Golmant and/or Nathan Lydick
 *
 */
public class ImageProcessor
{

/**
 * A class that holds several statistics about particles.
 * 
 * The measures include:
 * area: The area, in pixels of the blob
 * boundingRectLeft: The x coordinate of the left side of the blob
 * boundingRectTop: The y coordinate on the top of the bounding rectangle of the
 * blob
 * boundingRectRight: The x coordinate of a point which lies on the right side
 * of the bounding rectangle of the blob
 * boundingRectBottom: The y coordinate of a point which lies on the bottom side
 * of the bounding rectangle of the blob
 * center_mass_x: the weighted center of mass of the particle, If the particle
 * were a solid object of uniform density and thickness, this would be the x
 * coord of the balancing point.
 * center_mass_y: the weighted center of mass of the particle, If the particle
 * were a solid object of uniform density and thickness, this would be the y
 * coord of the balancing point.
 * imageHeight: The height of the image containing the blob
 * imageWidth: the width of the image containing the blob
 * boundingRectWidth: the width of the rectangle which would perfectly bound the
 * blob
 * PercentAreaToImageArea: The percent of the image this blob fills
 * ConvexHullArea: The area filled by the convex hull of the image
 * 
 * @author Kilroy
 *
 */
public class ParticleReport implements Comparator<ParticleReport>,
        Comparable<ParticleReport>
{
public double area;

// double BoundingRectLeft;
// double BoundingRectTop;
// double BoundingRectRight;
// double BoundingRectBottom;
public int boundingRectLeft;

public int boundingRectTop;

public int boundingRectRight;

public int boundingRectBottom;

public int center_mass_x;

public int center_mass_y;

public int imageHeight;

public int imageWidth;

public int boundingRectWidth;

public double PercentAreaToImageArea;

public double ConvexHullArea;

@Override
public int compare (ParticleReport r1, ParticleReport r2)
{
    return (int) (r1.area - r2.area);
}

@Override
public int compareTo (ParticleReport r)
{
    return (int) (r.area - this.area);
}
}

public enum DebugMode
    {
    DEBUG_NONE, DEBUG_ALL, DEBUG_ONLY_NON_ZERO, DEBUG_ABSOLUTE_LOCATION;
    }

private DebugMode debug = DebugMode.DEBUG_NONE;

private KilroyCamera camera = null;

private Image currentImage = null;

private VisionScript operators = new VisionScript();

// TODO use these values
private double offsetFromCenterX;// offset along line orthoganal to the primary
                                 // vector of travel that intersects the center
                                 // positive towards the starboard, negative
                                 // towards the port

private double offsetFromCenterY;// offset along primary vector of travel
                                 // positive is towards the front, negative
                                 // towards the back

// The focal length of the camera in pixels, use the formula below...
private double cameraFocalLengthPixels;
// =focal_pixel = (image_width_in_pixels * 0.5) / tan(Horiz_FOV * 0.5 * PI/180)

// the horizontal field of view of the camera
private double horizFieldOfView;

// The vertical angle, in radians, above the horizontal the camera points
private double cameraMountAngleAboveHorizontalRadians = 0;

private double cameraMountAngleToRightOfCenterRadians = 0;

/*
 * The pixel values of the camera resolution, x and y. Doubles because we have
 * to operate on them with decimals
 */
private double cameraXRes;

private double cameraYRes;

public ParticleReport[] reports = new ParticleReport[0];

private boolean newImageIsFresh = false;

private double visionGoalHeightFt = 0.0;// TODO setters and Getters.

/**
 * Creates an ImageProcessor object with camera <camera> and a default
 * processing script. The script consists of:
 * 1. LoadColorImageJPEGOperator
 * 2. ColorThresholdOperator
 * 3. RemoveSmallObjectsOperator
 * 4. ConvexHullOperator
 * 5. SaveBinaryImagePNGOperator
 * 
 * @param camera
 *            The KiloryCamera object that corresponds to the camera on the
 *            robot capturing images for processing. DO NOT PASS NULL HERE!
 */
public ImageProcessor (KilroyCamera camera)
{
    // this.operators.add(new SaveColorImageJPEGOperator(
    // "/home/lvuser/images/Test.jpg"));
    // this.camera.getImage().image;
    this(camera, new LoadColorImageJPEGOperator(
            "/home/lvuser/images/Firstpic.jpg"),
            new HSLColorThresholdOperator(0, 153, 0, 75, 5, 141),
            new RemoveSmallObjectsOperator(2, true),
            new ConvexHullOperator(true),
            new SaveBinaryImagePNGOperator(
                    "/home/lvuser/images/Out.png"));
}

/**
 * Creates a new ImageProcessor class with a camera and a custom vision
 * processing script.
 * 
 * @param camera
 *            The IP camera we'll use to capture images.
 * @param script
 *            The processing script object; it will be executed in the order
 *            they are organized in the object.
 */
public ImageProcessor (KilroyCamera camera, VisionScript script)
{
    this.camera = camera;
    this.operators = script;
    this.cameraXRes = camera.getHorizontalResolution();
    this.cameraYRes = camera.getVerticalResolution();
    this.cameraFocalLengthPixels = (this.cameraXRes / 2.0)
            / Math.tan(camera.getHorizFieldOfView() * .5
                    * (Math.PI / 180));
    // see formula commented below the variable
}

/**
 * Creates a new ImageProcessor class with a camera and a custom vision
 * processing script
 * 
 * @param camera
 *            The IP camera with which we will capture images.
 * @param ops
 *            A parameter list of VisionOperatorInterfaces, passed in order to
 *            the constructor.
 *            The constructor will create a VisionScript object the the
 *            parameter list in the same order it was received.
 */
public ImageProcessor (KilroyCamera camera,
        VisionOperatorInterface... ops)
{
    this.camera = camera;
    this.cameraXRes = camera.getHorizontalResolution();
    this.cameraYRes = camera.getVerticalResolution();
    this.operators = new VisionScript();
    this.cameraFocalLengthPixels = (this.cameraXRes / 2.0)
            / Math.tan(camera.getHorizFieldOfView() * .5
                    * (Math.PI / 180));
    for (VisionOperatorInterface operator : ops)
        {
        this.operators.put(operator);
        }
}

/**
 * 
 * @return
 *         An array containing ParticleReport objects for all our spotted blobs
 * @deprecated by Alex Kneipp, for reason:
 *             You should no longer need to use actual raw values, use the
 *             position methods such as getYawAngleToTarget, etc.
 */
@Deprecated
public ParticleReport[] getParticleAnalysisReports ()
{
    return this.reports;
}

public ParticleReport getLargestBlob ()
{
    if (this.reports != null && this.reports.length > 0)
        return this.reports[0];
    else
        return null;
}

public ParticleReport getSmallestBlob ()
{
    if (this.reports != null && this.reports.length > 0)
        return this.reports[this.reports.length - 1];
    else
        return null;
}

// TODO add a function to get the tallest of 2 blobs
public ParticleReport getNthSizeBlob (int n)
{
    if (this.reports != null && this.reports.length > 0)
        return this.reports[n];
    else
        return null;
}

/**
 * Changes the camera which captures images for processing
 * 
 * @param cam
 *            The camera to use for capturing images
 */
public void setCamera (KilroyCamera cam)
{
    this.camera = cam;
}

public void updateResolution ()
{
    this.cameraYRes = this.camera.getVerticalResolution();
    this.cameraXRes = this.camera.getHorizontalResolution();
}

/**
 * Applies all the operators in the VisionScript to the image and saves the
 * updated image to the currentImage field.
 */
// TODO make private?
public void applyOperators ()
{
    // Goes through all operators and applies whatever changes they are
    // programmed to apply. The currentImage is replaced with the altered
    // image.
    if (this.camera.gethaveCamera() == true && this.currentImage != null
            && this.newImageIsFresh == true)
        {
        for (int i = 0; i < operators.size(); i++)
            {
            this.currentImage = this.operators.get(i)
                    .operate(this.currentImage);
            }
        }
}

/**
 * Takes the current VisionScript controller for the class and replaces it with
 * the provided one.
 * 
 * @param newScript
 *            The VisionScript object with which to replace the processing
 *            script
 * @author
 *         Alexander H. Kneipp
 */
public void replaceVisionScript (VisionScript newScript)
{
    this.operators = newScript;
}

// TODO move the following methods to VisionScript and add a getVisionScript()
// method.
/**
 * Adds a new vision operator to the operator list, in zero-based position
 * <index>.
 * 
 * @param index
 *            The position in the list to add the operator (the list is
 *            traversed in order to process an image)
 * @param operator
 *            The VisionOperator to add to the list.
 * @author Alexander H. Kneipp
 */
public void addOperator (int index, VisionOperatorInterface operator)
{
    this.operators.add(index, operator);
}

/**
 * Removes the operator at position <index> in the processor list.
 * 
 * @param index
 *            The zero-based position from which to remove the operator.
 */
public void removeOperator (int index)
{
    this.operators.remove(index);
}

/**
 * Removes operators by operator type. Will only remove the first occurrence of
 * the operator unless <removeAllInstances> is true.
 * 
 * @param operatorToRemove
 *            The operator type to remove from the processing list.
 * @param removeAllInstances
 *            Boolean to determine if all occurrences of <operatorToRemove>.
 *            True removes all, false removes first.
 */
public void removeOperator (VisionOperatorInterface operatorToRemove,
        boolean removeAllInstances)
{
    for (int i = 0; i < this.operators.size(); i++)
        {
        // If the operator template is of the same class as the currently
        // viewed operator in the processor script, remove it
        if (operatorToRemove.getClass()
                .equals(this.operators.get(i).getClass()))
            {
            this.removeOperator(i);
            if (removeAllInstances == false)
                break;
            }
        }
}

/**
 * Removes all testing operators from the processing list.
 */
public void clearOperatorList ()
{
    operators.clear();
}

/**
 * Sets the angle the camera is mounted above the horizontal on the robot
 * 
 * @param mountAngle
 *            The angle above the horizontal the camera is mounted at, in
 *            radians
 */
public void setVerticalCameraMountAngle (double mountAngle)
{
    this.cameraMountAngleAboveHorizontalRadians = mountAngle;
}

/**
 * 
 * @return
 *         The angle above the horizontal at which the camera is mounted, in
 *         radians.
 */
public double getVerticalCameraMountAngle ()
{
    return this.cameraMountAngleAboveHorizontalRadians;
}

/**
 * Sets the angle to the right of the centerline of the camera (which is
 * parallel to the centerline of the robot) the camera is mounted, just in case
 * the builders decided it was a good idea to mount it off center.
 * 
 * @param angle
 *            The angle, in radians, to the right of center the camera is
 *            mounted (negative is to the left).
 */
public void setMountAngleToRightOfCenter (double angle)
{
    this.cameraMountAngleToRightOfCenterRadians = angle;
}

/**
 * @return
 *         The angle, in radians, to the right of center the camera is mounted
 *         (negative is to the left).
 */
public double getMountAngleToRightOfCenter ()
{
    return this.cameraMountAngleToRightOfCenterRadians;
}

/**
 * Pulls a new image from the camera and processes the image through the
 * operator list, only if the new image it received was fresh.
 */
public void processImage ()
{
    if (this.camera != null)
        {
        this.updateImage();
        if (this.newImageIsFresh == true)
            {
            this.applyOperators();
            this.updateParticalAnalysisReports();
            }
        }
}

/**
 * Processes the saved image without updating it.
 */
private void processImageNoUpdate ()
{
    this.applyOperators();
    this.updateParticalAnalysisReports();
}

/**
 * Captures an image from the camera given to the class.
 */
public void updateImage ()
{
    try
        {
        if (this.camera.freshImage() == true)
            {
            this.currentImage = this.camera.getImage().image;
            this.newImageIsFresh = true;
            }
        else
            {
            this.newImageIsFresh = false;
            }
        }
    catch (final NIVisionException e)
        {
        // Auto-generated catch block
        e.printStackTrace();
        }

}

/**
 * Takes the processed image and writes information on each particle (blob) into
 * the global <reports> array, in order of overall particle area.
 */
public void updateParticalAnalysisReports ()
{
    if (this.camera.gethaveCamera() == true
            && this.currentImage != null)
        {
        final int numParticles = NIVision
                .imaqCountParticles(this.currentImage, 0);

        System.out.println("Object removal blobs: " +
                NIVision.imaqCountParticles(this.currentImage, 0));

        // Measure particles and sort by particle size
        final Vector<ParticleReport> particles = new Vector<ParticleReport>();

        if (numParticles > 0)
            {

            for (int particleIndex = 0; particleIndex < numParticles; particleIndex++)
                {

                final ParticleReport particle = new ParticleReport();
                particle.PercentAreaToImageArea = NIVision
                        .imaqMeasureParticle(this.currentImage,
                                particleIndex, 0,
                                NIVision.MeasurementType.MT_AREA_BY_IMAGE_AREA);
                particle.area = NIVision.imaqMeasureParticle(
                        this.currentImage,
                        particleIndex, 0,
                        NIVision.MeasurementType.MT_AREA);
                particle.ConvexHullArea = NIVision
                        .imaqMeasureParticle(
                                this.currentImage,
                                particleIndex, 0,
                                NIVision.MeasurementType.MT_CONVEX_HULL_AREA);
                particle.boundingRectTop = (int) NIVision
                        .imaqMeasureParticle(this.currentImage,
                                particleIndex, 0,
                                NIVision.MeasurementType.MT_BOUNDING_RECT_TOP);
                particle.boundingRectLeft = (int) NIVision
                        .imaqMeasureParticle(this.currentImage,
                                particleIndex, 0,
                                NIVision.MeasurementType.MT_BOUNDING_RECT_LEFT);
                particle.boundingRectBottom = (int) NIVision
                        .imaqMeasureParticle(this.currentImage,
                                particleIndex, 0,
                                NIVision.MeasurementType.MT_BOUNDING_RECT_BOTTOM);
                particle.boundingRectRight = (int) NIVision
                        .imaqMeasureParticle(this.currentImage,
                                particleIndex, 0,
                                NIVision.MeasurementType.MT_BOUNDING_RECT_RIGHT);
                particle.boundingRectWidth = (int) NIVision
                        .imaqMeasureParticle(this.currentImage,
                                particleIndex, 0,
                                NIVision.MeasurementType.MT_BOUNDING_RECT_WIDTH);// par.boundingRectRight
                // -
                // par.boundingRectLeft;
                particle.center_mass_x = (int) NIVision
                        .imaqMeasureParticle(this.currentImage,
                                particleIndex, 0,
                                NIVision.MeasurementType.MT_CENTER_OF_MASS_X);
                particle.center_mass_y = (int) NIVision
                        .imaqMeasureParticle(this.currentImage,
                                particleIndex, 0,
                                NIVision.MeasurementType.MT_CENTER_OF_MASS_Y);
                particle.imageWidth = NIVision
                        .imaqGetImageSize(this.currentImage).width;
                particles.add(particle);
                }
            particles.sort(null);

            }
        this.reports = new ParticleReport[particles.size()];
        particles.copyInto(this.reports);
        }
}

/**
 * Finds the angle to the target to the right of center from the position of the
 * camera.
 * 
 * @param target
 *            The blob we're targeting
 * @return
 *         The yaw angle between the blob and the camera to the right of center
 *         (left is negative), in radians, or zero if the target is null.
 * 
 */
public double getYawAngleToTarget (ParticleReport target)
{
    double retVal;
    if (target != null)
        retVal = Math.atan((target.center_mass_x
                - ((this.cameraXRes / 2) - .5))
                / this.cameraFocalLengthPixels);
    else
        retVal = 0.0;
    if (this.debug == DebugMode.DEBUG_ALL
            || this.debug == DebugMode.DEBUG_ABSOLUTE_LOCATION)
        {
        System.out.println("Yaw Angle to target: " + retVal);
        }

    return retVal;
}

/**
 * Finds the angle to the target above the horizontal from the height of the
 * camera.
 * 
 * @param target
 *            The blob to calculate the angle to.
 * @return
 *         The pitch angle between the blob and the camera above the horizontal,
 *         in radians.
 */
public double getPitchAngleToTarget (ParticleReport target)
{
    if (target != null)
        {
        double adjustedYVal = this.cameraYRes
                - target.center_mass_y;
        // System.out.println("Vert Res: " + Hardware.drive.cameraYResolution);
        System.out.println(
                "Y coord " + target.center_mass_y);
        System.out.println(
                "X coord " + target.center_mass_x);
        return Math.atan((adjustedYVal - (this.cameraYRes / 2) - .5)
                / this.cameraFocalLengthPixels)
                + this.cameraMountAngleAboveHorizontalRadians;
        }
    return 0.0;
}

// TODO either define unit or make sure the programmer knows about all the units
/**
 * The distance from the front of the robot to the vertical plane of the target.
 * 
 * @param topTarget
 *            The blob we're targeting
 * @return
 *         The distance between the front of the robot and the vertical plane on
 *         which the target sits, in the unit of the height of the vision
 *         target.
 */
public double getZDistanceToFuelTarget (ParticleReport topTarget,
        ParticleReport bottomTarget)
{
    if (topTarget != null && bottomTarget != null)
        {
        // double yaw = this.getYawAngleToTarget(target);
        double topTargetPitch = this.getPitchAngleToTarget(topTarget);
        double bottomTargetPitch = this
                .getPitchAngleToTarget(bottomTarget);
        // System.out.println("Yaw angle: " + Math.toDegrees(yaw));
        System.out.println("Pitch angle of Bottom Target: "
                + Math.toDegrees(topTargetPitch));
        System.out.println("Pitch angle of Top Target: "
                + Math.toDegrees(bottomTargetPitch));

        double hypotenuse = (7 * Math.sin(90 - bottomTargetPitch))
                / Math.sin(topTargetPitch - bottomTargetPitch);
        double zDistance = Math.sqrt((hypotenuse * hypotenuse) - 6241);
        // System.out.println(
        // "Old Distance: " + this.visionGoalHeight
        // * Math.cos(yaw)
        // / Math.tan(pitch));
        // System.out.println("New Distance: " +
        // (Math.sin(getPitchAngleToTarget(target)
        // / this.visionGoalHeight)
        // * Math.cos(this.getPitchAngleToTarget(target))
        // * Math.sin(this.getYawAngleToTarget(target))));
        // return (this.visionGoalHeightFt
        // * Math.cos(yaw)
        // / Math.tan(pitch))/* * 2.0 */;
        return zDistance;
        }
    return -1.0;
}

public double getZDistanceToGearTarget (ParticleReport leftTarget,
        ParticleReport rightTarget)
{
    if (leftTarget != null && rightTarget != null)
        {
        double leftTargetYaw = this.getYawAngleToTarget(leftTarget);
        double rightTargetYaw = this.getYawAngleToTarget(rightTarget);

        double totalAngleBetweenTargets = (-leftTargetYaw)
                + rightTargetYaw;
        // TODO Fix this for future use.
        return -1.0;
        }
    else
        {
        return -1.0;
        }
}

/**
 * Returns the number of pixels away the center of the robot is from the
 * center of the 2 blobs.
 * 
 * @return double
 */
public double getPositionOfRobotToGear (ParticleReport leftTarget,
        ParticleReport rightTarget, double compareValue)
{
    if (leftTarget != null && rightTarget != null)
        {
        return compareValue - ((leftTarget.center_mass_x
                + rightTarget.center_mass_x) / 2.0);
        }
    return 0;
}

/**
 * Gets the position of the two gear targets based on the 2 blobs'
 * location
 * 
 * @param target1
 * @param target2
 * @return RIGHT if target1 is right of target2, LEFT if the other way
 *         around.
 */
public Position getPositionOfBlob (ParticleReport target1,
        ParticleReport target2)
{
    if (target1.center_mass_x > target2.center_mass_x)
        return Position.RIGHT;
    else
        return Position.LEFT;

}

public static enum Position
    {
    LEFT, RIGHT
    }

// Positive right, negative left
/**
 * See getYawAngleToTarget (ParticleReport).
 * 
 * @author Alex Kneipp
 * @deprecated by Alex Kneipp
 *             Use getYawAngleToTarget (ParticleReport) instead.
 * @param targetIndex
 *            The index of the target blob in the reports array
 * @return
 *         The yaw angle to the target, or zero if the reports array does not
 *         exist or the int argument is beyond the bounds of the array.
 *         See getYawAngleToTarget (ParticleReport) for more information on the
 *         return.
 */
@Deprecated
public double getYawAngleToTarget (int targetIndex)
{
    if (this.reports != null && targetIndex < this.reports.length)
        {
        return this.getYawAngleToTarget(this.reports[targetIndex]);
        }
    return 0;
}

/**
 * See getPitchAngleToTarget (ParticleReport).
 * 
 * @author Alex Kneipp
 * 
 * @deprecated by Alex Kneipp
 *             Use getPitchAngleToTarget (ParticleReport) instead.
 * @param targetIndex
 *            The index of the target blob in the reports array
 * @return
 *         The pitch angle to the target, or zero if the reports array does not
 *         exist or the int argument is beyond the bounds of the array.
 *         See getPitchAngleToTarget (ParticleReport) for more information on
 *         the return.
 */
@Deprecated
public double getPitchAngleToTarget (int targetIndex)
{
    if (this.reports != null && targetIndex < this.reports.length)
        {
        return this.getPitchAngleToTarget(this.reports[targetIndex]);
        }
    return 0;
}

/**
 * See getZDistanceToTarget (ParticleReport).
 * 
 * @author Alex Kneipp
 * @deprecated by Alex Kneipp
 *             Use getZDistanceToTarget (ParticleReport) instead.
 * @param targetIndex
 *            The index of the target blob in the reports array
 * @return
 *         The ZDistance to thhe target, or zero if the reports array does not
 *         exist or the int argument is beyond the bounds of the array.
 *         See getZDistanceToTarget (ParticleReport) for more information on the
 *         return.
 */
// @Deprecated
// public double getZDistanceToTargetFT (int targetIndex)
// {
// if (this.reports != null && targetIndex < this.reports.length)
// {
// return this.getZDistanceToTarget(this.reports[targetIndex]);
// }
// return 0.0;
// }
}
