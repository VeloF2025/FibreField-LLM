// 🟢 WORKING: Photo type definitions with validation requirements
package com.fibreflow.core.common.constants

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Enumeration of all photo types required during installation workflow.
 * Each type has specific validation requirements and AI processing needs.
 */
@Parcelize
enum class PhotoType(
    val displayName: String,
    val description: String,
    val isRequired: Boolean,
    val sequence: Int,
    val validationRequirements: List<ValidationRequirement>
) : Parcelable {
    
    CABLE_SPAN(
        displayName = "Cable Span",
        description = "Photo showing the full cable span from pole to house with drop number visible",
        isRequired = true,
        sequence = 1,
        validationRequirements = listOf(
            ValidationRequirement.DROP_NUMBER_VISIBLE,
            ValidationRequirement.CABLE_VISIBLE,
            ValidationRequirement.POLE_TO_HOUSE_VIEW,
            ValidationRequirement.NO_DAMAGE_VISIBLE
        )
    ),
    
    HOME_ENTRY(
        displayName = "Home Entry Point",
        description = "Photo of where the cable enters the customer's property",
        isRequired = true,
        sequence = 2,
        validationRequirements = listOf(
            ValidationRequirement.ENTRY_POINT_CLEAR,
            ValidationRequirement.CABLE_ROUTING_VISIBLE,
            ValidationRequirement.PROPERTY_BOUNDARY_CLEAR
        )
    ),
    
    ONT_BARCODE(
        displayName = "ONT Barcode/Serial",
        description = "Clear photo of the ONT barcode and serial number",
        isRequired = true,
        sequence = 3,
        validationRequirements = listOf(
            ValidationRequirement.BARCODE_READABLE,
            ValidationRequirement.SERIAL_NUMBER_VISIBLE,
            ValidationRequirement.TEXT_CLARITY,
            ValidationRequirement.ONT_DEVICE_VISIBLE
        )
    ),
    
    ONT_ACTIVE(
        displayName = "ONT Active Lights",
        description = "Photo showing all 4 ONT lights active (Power, LOS, PON, LAN)",
        isRequired = true,
        sequence = 4,
        validationRequirements = listOf(
            ValidationRequirement.FOUR_LIGHTS_GREEN,
            ValidationRequirement.ONT_DEVICE_VISIBLE,
            ValidationRequirement.DROP_NUMBER_VISIBLE,
            ValidationRequirement.LIGHTS_CLEARLY_VISIBLE
        )
    ),
    
    ROUTER_CONNECTED(
        displayName = "Router Connected",
        description = "Photo showing router powered on and connected to ONT",
        isRequired = true,
        sequence = 5,
        validationRequirements = listOf(
            ValidationRequirement.ROUTER_POWERED_ON,
            ValidationRequirement.CONNECTION_CABLES_VISIBLE,
            ValidationRequirement.STATUS_LIGHTS_VISIBLE
        )
    ),
    
    SPEED_TEST(
        displayName = "Speed Test Results",
        description = "Screenshot of speed test results showing achieved speeds",
        isRequired = true,
        sequence = 6,
        validationRequirements = listOf(
            ValidationRequirement.SPEED_VALUES_VISIBLE,
            ValidationRequirement.TIMESTAMP_VISIBLE,
            ValidationRequirement.SPEED_MEETS_MINIMUM
        )
    ),
    
    CLEANUP(
        displayName = "Site Cleanup",
        description = "Photo showing work area cleaned up after installation",
        isRequired = true,
        sequence = 7,
        validationRequirements = listOf(
            ValidationRequirement.AREA_CLEAN,
            ValidationRequirement.NO_DEBRIS_VISIBLE,
            ValidationRequirement.TOOLS_REMOVED
        )
    ),
    
    CUSTOMER_SIGNATURE(
        displayName = "Customer Signature",
        description = "Digital signature pad showing customer approval",
        isRequired = true,
        sequence = 8,
        validationRequirements = listOf(
            ValidationRequirement.SIGNATURE_PRESENT,
            ValidationRequirement.NAME_LEGIBLE,
            ValidationRequirement.TIMESTAMP_VISIBLE
        )
    ),
    
    COMPLETED_INSTALLATION(
        displayName = "Final Installation",
        description = "Overview photo of the completed installation",
        isRequired = true,
        sequence = 9,
        validationRequirements = listOf(
            ValidationRequirement.INSTALLATION_COMPLETE,
            ValidationRequirement.EQUIPMENT_SECURED,
            ValidationRequirement.CUSTOMER_PROPERTY_INTACT
        )
    ),
    
    // Optional photos for specific scenarios
    UNDERGROUND_CABLE(
        displayName = "Underground Cable",
        description = "Photo of underground cable routing if applicable",
        isRequired = false,
        sequence = 10,
        validationRequirements = listOf(
            ValidationRequirement.CABLE_PROTECTION_VISIBLE,
            ValidationRequirement.DEPTH_ADEQUATE
        )
    ),
    
    EQUIPMENT_ROOM(
        displayName = "Equipment Room",
        description = "Photo of network equipment room setup",
        isRequired = false,
        sequence = 11,
        validationRequirements = listOf(
            ValidationRequirement.EQUIPMENT_ORGANIZED,
            ValidationRequirement.VENTILATION_ADEQUATE
        )
    ),
    
    ISSUE_DOCUMENTATION(
        displayName = "Issue Documentation",
        description = "Photo documenting any issues encountered",
        isRequired = false,
        sequence = 12,
        validationRequirements = listOf(
            ValidationRequirement.ISSUE_CLEARLY_VISIBLE,
            ValidationRequirement.CONTEXT_PROVIDED
        )
    );
    
    /**
     * Get the next required photo type in sequence
     */
    fun getNextRequired(): PhotoType? {
        return values()
            .filter { it.isRequired && it.sequence > this.sequence }
            .minByOrNull { it.sequence }
    }
    
    /**
     * Get all photo types up to this one in sequence
     */
    fun getPreviousTypes(): List<PhotoType> {
        return values()
            .filter { it.sequence < this.sequence }
            .sortedBy { it.sequence }
    }
    
    /**
     * Check if this is the final required photo
     */
    fun isFinalRequired(): Boolean {
        return this == values()
            .filter { it.isRequired }
            .maxByOrNull { it.sequence }
    }
}

/**
 * Validation requirements for photo types
 */
@Parcelize
enum class ValidationRequirement(
    val displayName: String,
    val description: String,
    val aiModelRequired: Boolean = true
) : Parcelable {
    
    // General requirements
    DROP_NUMBER_VISIBLE("Drop Number Visible", "Drop number label must be clearly visible", true),
    TEXT_CLARITY("Text Clarity", "All text must be readable", true),
    
    // Equipment requirements
    ONT_DEVICE_VISIBLE("ONT Device Visible", "ONT device must be clearly visible", true),
    ROUTER_POWERED_ON("Router Powered", "Router must show power indicators", true),
    
    // Light/Status requirements
    FOUR_LIGHTS_GREEN("Four Green Lights", "All 4 ONT status lights must be green", true),
    LIGHTS_CLEARLY_VISIBLE("Lights Clearly Visible", "Status lights must be clearly visible", true),
    STATUS_LIGHTS_VISIBLE("Status Lights Visible", "Device status lights must be visible", true),
    
    // Cable and installation requirements
    CABLE_VISIBLE("Cable Visible", "Fiber cable must be visible in photo", true),
    POLE_TO_HOUSE_VIEW("Pole to House View", "Full span from pole to house must be visible", false),
    CONNECTION_CABLES_VISIBLE("Connection Cables", "All connection cables must be visible", true),
    CABLE_PROTECTION_VISIBLE("Cable Protection", "Cable protection/conduit must be visible", true),
    
    // Quality and safety requirements
    NO_DAMAGE_VISIBLE("No Damage", "No visible damage to cables or equipment", true),
    NO_DEBRIS_VISIBLE("No Debris", "Work area must be free of debris", true),
    AREA_CLEAN("Area Clean", "Work area must be properly cleaned", false),
    
    // Specific validation requirements
    BARCODE_READABLE("Barcode Readable", "Barcode must be scannable/readable", true),
    SERIAL_NUMBER_VISIBLE("Serial Number Visible", "Serial number must be clearly visible", true),
    SIGNATURE_PRESENT("Signature Present", "Customer signature must be present", false),
    NAME_LEGIBLE("Name Legible", "Customer name must be legible", false),
    TIMESTAMP_VISIBLE("Timestamp Visible", "Timestamp must be visible", false),
    
    // Speed test requirements
    SPEED_VALUES_VISIBLE("Speed Values Visible", "Download/upload speeds must be visible", true),
    SPEED_MEETS_MINIMUM("Speed Meets Minimum", "Speeds must meet minimum requirements", false),
    
    // Property and setup requirements
    ENTRY_POINT_CLEAR("Entry Point Clear", "Cable entry point must be clearly visible", false),
    CABLE_ROUTING_VISIBLE("Cable Routing Visible", "Cable routing path must be visible", false),
    PROPERTY_BOUNDARY_CLEAR("Property Boundary Clear", "Property boundaries must be clear", false),
    INSTALLATION_COMPLETE("Installation Complete", "Installation must appear complete", false),
    EQUIPMENT_SECURED("Equipment Secured", "All equipment must be properly secured", true),
    CUSTOMER_PROPERTY_INTACT("Property Intact", "Customer property must be undamaged", false),
    
    // Underground and specialized requirements
    DEPTH_ADEQUATE("Adequate Depth", "Underground cable depth must be adequate", false),
    EQUIPMENT_ORGANIZED("Equipment Organized", "Equipment must be neatly organized", false),
    VENTILATION_ADEQUATE("Adequate Ventilation", "Equipment ventilation must be adequate", false),
    
    // Issue documentation
    ISSUE_CLEARLY_VISIBLE("Issue Clearly Visible", "Documented issue must be clearly visible", false),
    CONTEXT_PROVIDED("Context Provided", "Sufficient context must be provided", false),
    TOOLS_REMOVED("Tools Removed", "All tools must be removed from work area", true);
    
    /**
     * Check if this requirement needs AI validation
     */
    fun requiresAI(): Boolean = aiModelRequired
}

/**
 * Get all required photo types in sequence order
 */
fun getRequiredPhotoTypes(): List<PhotoType> {
    return PhotoType.values()
        .filter { it.isRequired }
        .sortedBy { it.sequence }
}

/**
 * Get photo type by sequence number
 */
fun getPhotoTypeBySequence(sequence: Int): PhotoType? {
    return PhotoType.values().find { it.sequence == sequence }
}

/**
 * Calculate installation progress based on completed photo types
 */
fun calculateProgress(completedTypes: List<PhotoType>): Float {
    val requiredTypes = getRequiredPhotoTypes()
    val completedRequired = completedTypes.filter { it.isRequired }.size
    return if (requiredTypes.isEmpty()) 0f else completedRequired.toFloat() / requiredTypes.size
}