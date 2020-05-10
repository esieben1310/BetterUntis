package com.sapuseven.untis.fragments

import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.sapuseven.untis.R
import com.sapuseven.untis.data.connectivity.UntisApiConstants.CAN_READ_LESSON_TOPIC
import com.sapuseven.untis.data.connectivity.UntisApiConstants.CAN_READ_STUDENT_ABSENCE
import com.sapuseven.untis.data.timetable.PeriodData
import com.sapuseven.untis.data.timetable.TimegridItem
import com.sapuseven.untis.helpers.ConversionUtils
import com.sapuseven.untis.helpers.KotlinUtils.safeLet
import com.sapuseven.untis.helpers.timetable.TimetableDatabaseInterface
import com.sapuseven.untis.models.untis.timetable.Period
import com.sapuseven.untis.models.untis.timetable.PeriodElement
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat


class TimetableItemDetailsFragment : Fragment() {
	private var periodData: PeriodData? = null
	private var timetableDatabaseInterface: TimetableDatabaseInterface? = null

	private lateinit var listener: TimetableItemDetailsDialogListener

	companion object {
		val HOMEWORK_DUE_TIME_FORMAT: DateTimeFormatter = ISODateTimeFormat.date()

		const val STATE_ITEM_DATA = "com.sapuseven.untis.state.itemdata"
		const val STATE_DATABASE_INTERFACE = "com.sapuseven.untis.state.databaseinterface"

		fun createInstance(item: TimegridItem, timetableDatabaseInterface: TimetableDatabaseInterface?): TimetableItemDetailsFragment =
				TimetableItemDetailsFragment().apply {
					this.periodData = item.periodData
					this.timetableDatabaseInterface = timetableDatabaseInterface
				}
	}

	interface TimetableItemDetailsDialogListener {
		fun onPeriodElementClick(fragment: Fragment, element: PeriodElement?, useOrgId: Boolean)

		fun onPeriodAbsencesClick(fragment: Fragment, element: Period)
	}

	override fun onAttach(context: Context) {
		super.onAttach(context)
		if (context is TimetableItemDetailsDialogListener)
			listener = context
		else
			throw ClassCastException("$context must implement TimetableItemDetailsDialogListener")
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		savedInstanceState?.let {
			this.periodData = savedInstanceState.getSerializable(STATE_ITEM_DATA) as PeriodData?
			this.timetableDatabaseInterface = savedInstanceState.getSerializable(STATE_DATABASE_INTERFACE) as TimetableDatabaseInterface?
		}

		return activity?.let { activity ->
			safeLet(periodData, timetableDatabaseInterface) { periodData, timetableDatabaseInterface ->
				generateView(activity, container, periodData, timetableDatabaseInterface)
			} ?: generateErrorView(activity, container)
		} ?: throw IllegalStateException("Activity cannot be null")
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putSerializable(STATE_ITEM_DATA, periodData)
		outState.putSerializable(STATE_DATABASE_INTERFACE, timetableDatabaseInterface)
	}

	private fun generateView(activity: FragmentActivity, container: ViewGroup?, periodData: PeriodData, timetableDatabaseInterface: TimetableDatabaseInterface): View {
		val root = activity.layoutInflater.inflate(R.layout.fragment_timetable_item_details_page, container, false) as LinearLayout

		val attrs = intArrayOf(android.R.attr.textColorPrimary)
		val ta = context?.obtainStyledAttributes(attrs)
		val color = ta?.getColor(0, 0)
		ta?.recycle()

		setOf(
				periodData.element.text.lesson,
				periodData.element.text.substitution,
				periodData.element.text.info
		).forEach {
			if (it.isNotBlank())
				activity.layoutInflater.inflate(R.layout.fragment_timetable_item_details_page_info, root, false).run {
					(findViewById<TextView>(R.id.tvInfo)).text = it
					root.addView(this)
				}
		}

		periodData.element.homeWorks?.forEach {
			val endDate = HOMEWORK_DUE_TIME_FORMAT.parseDateTime(it.endDate)

			activity.layoutInflater.inflate(R.layout.fragment_timetable_item_details_page_homework, root).run {
				(findViewById<TextView>(R.id.textview_roomfinder_name)).text = it.text
				(findViewById<TextView>(R.id.tvDate)).text = getString(R.string.homeworks_due_time, endDate.toString(getString(R.string.homeworks_due_time_format)))
			}
		}

		if (periodData.element.can.contains(CAN_READ_STUDENT_ABSENCE))
			activity.layoutInflater.inflate(R.layout.fragment_timetable_item_details_page_absences, root, false).run {
				setOnClickListener {
					listener.onPeriodAbsencesClick(this@TimetableItemDetailsFragment, periodData.element)
				}
				root.addView(this)
			}

		if (periodData.element.can.contains(CAN_READ_LESSON_TOPIC))
			activity.layoutInflater.inflate(R.layout.fragment_timetable_item_details_page_lessontopic, root)

		val teacherList = root.findViewById<LinearLayout>(R.id.llTeacherList)
		val klassenList = root.findViewById<LinearLayout>(R.id.llClassList)
		val roomList = root.findViewById<LinearLayout>(R.id.llRoomList)

		if (populateList(timetableDatabaseInterface, teacherList, periodData.teachers.toList(), TimetableDatabaseInterface.Type.TEACHER, color))
			root.findViewById<View>(R.id.llTeachers).visibility = View.GONE
		if (populateList(timetableDatabaseInterface, klassenList, periodData.classes.toList(), TimetableDatabaseInterface.Type.CLASS, color))
			root.findViewById<View>(R.id.llClasses).visibility = View.GONE
		if (populateList(timetableDatabaseInterface, roomList, periodData.rooms.toList(), TimetableDatabaseInterface.Type.ROOM, color))
			root.findViewById<View>(R.id.llRooms).visibility = View.GONE

		if (periodData.subjects.size > 0) {
			var title = periodData.getLong(periodData.subjects, TimetableDatabaseInterface.Type.SUBJECT)
			if (periodData.isCancelled())
				title = getString(R.string.all_lesson_cancelled, title)
			if (periodData.isIrregular())
				title = getString(R.string.all_lesson_irregular, title)
			if (periodData.isExam())
				title = getString(R.string.all_lesson_exam, title)

			(root.findViewById(R.id.title) as TextView).text = title
		} else {
			root.findViewById<View>(R.id.title).visibility = View.GONE
		}
		return root
	}

	private fun generateErrorView(activity: FragmentActivity, container: ViewGroup?): View {
		return activity.layoutInflater.inflate(R.layout.fragment_timetable_item_details_page_error, container, false)
	}

	private fun populateList(timetableDatabaseInterface: TimetableDatabaseInterface,
	                         list: LinearLayout,
	                         data: List<PeriodElement>,
	                         type: TimetableDatabaseInterface.Type,
	                         textColor: Int?): Boolean {
		if (data.isEmpty()) return true
		data.forEach { element ->
			generateTextViewForElement(element, type, timetableDatabaseInterface, textColor, false)?.let { list.addView(it) }
			if (element.id != element.orgId)
				generateTextViewForElement(element, type, timetableDatabaseInterface, textColor, true)?.let { list.addView(it) }
		}
		return false
	}

	private fun generateTextViewForElement(element: PeriodElement,
	                                       type: TimetableDatabaseInterface.Type,
	                                       timetableDatabaseInterface: TimetableDatabaseInterface,
	                                       textColor: Int?,
	                                       useOrgId: Boolean = false): TextView? {
		val tv = TextView(requireContext())
		val params = LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.MATCH_PARENT)
		params.setMargins(0, 0, ConversionUtils.dpToPx(12.0f, requireContext()).toInt(), 0)
		tv.text = timetableDatabaseInterface.getShortName(if (useOrgId) element.orgId else element.id, type)
		if (tv.text.isBlank()) return null
		tv.layoutParams = params
		textColor?.let { tv.setTextColor(it) }
		tv.gravity = Gravity.CENTER_VERTICAL
		if (useOrgId) {
			tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
		}
		tv.setOnClickListener {
			listener.onPeriodElementClick(this, element, useOrgId)
		}
		return tv
	}
}

private fun <E> List<E>.containsAny(vararg items: E): Boolean = this.any(items.toSet()::contains)
