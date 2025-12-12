package it.unisannio.muses.data.models

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

data class Mission(
    @SerializedName("_id") val id: String,
    val status: String,
    val steps: List<MissionStep>,
    @SerializedName("user_id") val userId: String
)

data class MissionStep(
    val completed: Boolean,
    @SerializedName("step_id") val stepId: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readByte() != 0.toByte(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (completed) 1 else 0)
        parcel.writeString(stepId)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MissionStep> {
        override fun createFromParcel(parcel: Parcel): MissionStep {
            return MissionStep(parcel)
        }

        override fun newArray(size: Int): Array<MissionStep?> {
            return arrayOfNulls(size)
        }
    }
}
