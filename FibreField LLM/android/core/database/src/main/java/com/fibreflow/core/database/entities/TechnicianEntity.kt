// 🟢 WORKING: Technician entity with role, certifications, and project assignments
package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.fibreflow.core.database.converters.DateConverters
import java.util.*

/**
 * Entity representing a fiber installation technician
 * Contains profile, certifications, and project access information
 */
@Entity(
    tableName = "technicians",
    indices = [
        Index(value = ["active"]),
        Index(value = ["role"]),
        Index(value = ["email"]),
        Index(value = ["last_login"])
    ]
)
@TypeConverters(DateConverters::class)
data class TechnicianEntity(
    @PrimaryKey
    @ColumnInfo(name = "technician_id")
    val technicianId: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "email")
    val email: String? = null,
    
    @ColumnInfo(name = "phone")
    val phone: String? = null,
    
    @ColumnInfo(name = "role")
    val role: TechnicianRole,
    
    /**
     * JSON array of certifications the technician holds
     * Example: ["Fiber Optic Installation", "ONT Configuration", "Safety Training"]
     */
    @ColumnInfo(name = "certifications")
    val certifications: String? = null,
    
    /**
     * JSON array of project IDs the technician has access to
     * Example: [1, 3, 5, 7]
     */
    @ColumnInfo(name = "active_projects")
    val activeProjects: String? = null,
    
    /**
     * JSON array of permissions/capabilities
     * Example: ["INSTALL_FIBER", "ACTIVATE_SERVICE", "CREATE_REMEDIATION"]
     */
    @ColumnInfo(name = "permissions")
    val permissions: String? = null,
    
    @ColumnInfo(name = "active")
    val active: Boolean = true,
    
    @ColumnInfo(name = "last_login")
    val lastLogin: Date? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date
) {
    
    /**
     * Check if technician is currently active and can work
     */
    val canWork: Boolean
        get() = active && role != TechnicianRole.SUSPENDED
    
    /**
     * Check if technician is a supervisor or higher
     */
    val isSupervisor: Boolean
        get() = role in listOf(TechnicianRole.SUPERVISOR, TechnicianRole.MANAGER, TechnicianRole.ADMIN)
    
    /**
     * Check if technician has administrative privileges
     */
    val isAdmin: Boolean
        get() = role == TechnicianRole.ADMIN
    
    /**
     * Check if technician can approve work
     */
    val canApproveWork: Boolean
        get() = role in listOf(TechnicianRole.SENIOR_TECHNICIAN, TechnicianRole.SUPERVISOR, TechnicianRole.MANAGER, TechnicianRole.ADMIN)
    
    /**
     * Check if technician can handle complex installations
     */
    val canHandleComplexInstallations: Boolean
        get() = role in listOf(TechnicianRole.SENIOR_TECHNICIAN, TechnicianRole.SUPERVISOR, TechnicianRole.MANAGER, TechnicianRole.ADMIN)
    
    /**
     * Check if technician has been active recently (within last 30 days)
     */
    val isRecentlyActive: Boolean
        get() = lastLogin?.let { login ->
            val thirtyDaysAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -30)
            }.time
            login.after(thirtyDaysAgo)
        } ?: false
    
    /**
     * Get display text for role
     */
    val roleDisplayText: String
        get() = when (role) {
            TechnicianRole.TRAINEE -> "Trainee"
            TechnicianRole.TECHNICIAN -> "Technician"
            TechnicianRole.SENIOR_TECHNICIAN -> "Senior Technician"
            TechnicianRole.SUPERVISOR -> "Supervisor"
            TechnicianRole.MANAGER -> "Manager"
            TechnicianRole.ADMIN -> "Administrator"
            TechnicianRole.SUSPENDED -> "Suspended"
        }
    
    /**
     * Get technician's initials for display
     */
    val initials: String
        get() = name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
}

/**
 * Technician role enumeration with hierarchy
 */
enum class TechnicianRole(val hierarchy: Int) {
    SUSPENDED(0),           // No access
    TRAINEE(1),            // Limited access, requires supervision
    TECHNICIAN(2),         // Standard installation work
    SENIOR_TECHNICIAN(3),  // Complex installations, can approve basic work
    SUPERVISOR(4),         // Team leadership, work approval
    MANAGER(5),            // Project management, resource allocation
    ADMIN(6);              // Full system access

    /**
     * Check if this role has higher or equal authority than another role
     */
    fun hasAuthorityOver(other: TechnicianRole): Boolean {
        return this.hierarchy >= other.hierarchy
    }
    
    /**
     * Check if this role can supervise another role
     */
    fun canSupervise(other: TechnicianRole): Boolean {
        return this.hierarchy > other.hierarchy && this != SUSPENDED
    }
}

/**
 * Technician permissions enumeration
 */
enum class TechnicianPermission {
    // Basic installation permissions
    INSTALL_FIBER,
    CAPTURE_PHOTOS,
    VALIDATE_INSTALLATION,
    
    // Equipment permissions
    CONFIGURE_ONT,
    ACTIVATE_SERVICE,
    RUN_SPEED_TESTS,
    
    // Documentation permissions
    CREATE_REMEDIATION,
    APPROVE_REMEDIATION,
    GENERATE_REPORTS,
    
    // Administrative permissions
    MANAGE_TECHNICIANS,
    ASSIGN_WORK,
    OVERRIDE_VALIDATIONS,
    
    // System permissions
    SYNC_DATA,
    EXPORT_DATA,
    MANAGE_PROJECTS
}