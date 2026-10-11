import type { HorseSpecialty } from '@/features/horses/types';

export type PlanStatus = 'UPCOMING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type WorkoutStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
export type SurfaceType = 'TURF' | 'DIRT' | 'SYNTHETIC';
export type IntensityLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export type TrainingDay =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

/** Group of a subject. A plain enum like surface type and intensity: it has no table of its own. */
export type SubjectCategory = 'ENDURANCE' | 'SPEED' | 'GATE_START' | 'RECOVERY';

export interface Subject {
  id: number;
  category: SubjectCategory;
  name: string;
  description: string | null;
  surfaceType: SurfaceType;
  targetDistanceMeters: number;
  intensityLevel: IntensityLevel;
  durationMinutes: number;
}

export interface CreateSubjectRequest {
  category: SubjectCategory;
  name: string;
  description?: string;
  surfaceType: SurfaceType;
  targetDistanceMeters: number;
  intensityLevel: IntensityLevel;
  durationMinutes: number;
}

export interface CourseSubjectItem {
  subjectId: number;
  orderIndex: number;
}

export interface Course {
  id: number;
  name: string;
  description: string | null;
  targetGoal: string | null;
  totalSessions: number;
}

export interface CourseSubjectResponse {
  id: number;
  courseId: number;
  subjectId: number;
  orderIndex: number;
  subjectName: string;
  durationMinutes: number;
}

export interface CourseDetailResponse {
  course: Course;
  subjects: CourseSubjectResponse[];
}

export interface CreateCourseRequest {
  name: string;
  description?: string;
  targetGoal?: string;
  totalSessions: number;
  subjects: CourseSubjectItem[];
}

export interface HorseEnrollmentItem {
  horseId: number;
  groomId?: number | null;
}

export interface CreateHorseTrainingPlanRequest {
  courseId: number;
  startDate: string; // YYYY-MM-DD
  trainingDays: TrainingDay[];
  notes?: string;
  horses: HorseEnrollmentItem[];
}

export interface JoinableCohortResponse {
  cohortStatus: 'UPCOMING' | 'ACTIVE';
  courseId: number;
  courseName: string;
  suggestedStartDate: string;
  trainingDays: TrainingDay[];
  horseCount: number;
  /**
   * Number of IN-PHASE sessions — same day, same subject.
   * NOT "sessions in the same lot": being in phase is only a NECESSARY condition.
   * The group is still split across lots if over capacity (BR-10) or sharing a Groom (BR-09).
   */
  sharedSessions: number;
  totalSessions: number;
  waitDays: number;
  /** Capacity of one lot, returned by the backend so the number 6 is not hardcoded. */
  lotCapacity: number;
  note: string;
}

export interface PlanWorkoutItemResponse {
  workoutId: number;
  lotId: number;
  lotDate: string;
  startTime: string;
  endTime: string;
  subjectId: number;
  subjectName: string;
  horseId: number;
  /** The handler of THIS horse only — NOT "the lot's groom". */
  assignedGroomId: number | null;
  /** Total active horses in the lot, to show how group matching works. */
  lotOccupancy: number | null;
  status: WorkoutStatus;
  actualDistanceMeters: number | null;
  averageHeartRate: number | null;
  maxHeartRate: number | null;
  topSpeedKmh: number | null;
  performanceRating: number | null;
  trainerFeedback: string | null;
}

export interface HorseTrainingPlan {
  id: number;
  horseId: number;
  courseId: number;
  startDate: string;
  endDate: string;
  status: PlanStatus;
  notes: string | null;
}

export interface HorseTrainingPlanDetailResponse {
  plan: HorseTrainingPlan;
  workouts: PlanWorkoutItemResponse[];
  horseName: string | null;
  courseName: string | null;
}

/** One row in the training plan list. */
export interface PlanSummaryResponse {
  planId: number;
  horseId: number;
  horseName: string;
  courseId: number;
  courseName: string;
  startDate: string;
  endDate: string;
  status: PlanStatus;
  totalSessions: number;
  completedSessions: number;
  /** Cancelled sessions — removed from the denominator when computing progress. */
  cancelledSessions: number;
}

export interface CompleteWorkoutRequest {
  actualDistanceMeters?: number | null;
  actualDurationMinutes?: number | null;
  topSpeedKmh?: number | null;
  averageSpeedKmh?: number | null;
  averageHeartRate?: number | null;
  maxHeartRate?: number | null;
  recoveryHeartRate?: number | null;
  performanceRating: number; // 1-10
  trainerFeedback?: string | null;
  videoUrl?: string | null;
}

export interface TrainingLotResponse {
  lotId: number;
  lotDate: string;
  startTime: string; // "06:00:00"
  endTime: string;   // "07:30:00"
  subjectId: number;
  subjectName: string;
  durationMinutes: number;
  status: WorkoutStatus;
  maxCapacity: number;
  occupied: number;
  remainingSlots: number;
  horseNames: string[];
}

export interface RescheduleLotRequest {
  newStartTime: string; // "HH:mm" or "HH:mm:ss"
  reason?: string;
}

export interface HorseFitnessTrendItem {
  workoutId: number;
  date: string; // YYYY-MM-DD
  subjectName: string;
  distanceMeters: number | null;
  averageSpeedKmh: number | null;
  topSpeedKmh: number | null;
  averageHeartRate: number | null;
  maxHeartRate: number | null;
  recoveryHeartRate: number | null;
  performanceRating: number | null;
}

export interface HorseAlert {
  ruleCode: string;
  severity: 'WARNING' | 'DANGER';
  title: string;
  description: string;
  triggeredAt: string;
  metricValue: number;
  thresholdValue: number;
}

export interface TrainerDashboardHorse {
  horseId: number;
  horseName: string;
  breed: string | null;
  stallCode: string | null;
  planId: number | null;
  courseName: string;
  /** NONE when the horse has no plan yet. */
  planStatus: PlanStatus | 'NONE';
  completedSessions: number;
  totalSessions: number;
  progressPercent: number;
  latestPerformanceRating: number | null;
  avgPerformanceRating30d: number | null;
  alertsCount: number;
  alertTitles: string[];
  startDate: string | null;
  endDate: string | null;
  specialty: HorseSpecialty;
}

/** Training rules the backend enforces, published so the UI does not hard-code them. */
export interface TrainingRules {
  goldenHoursStart: string; // "06:00:00"
  goldenHoursEnd: string;   // "10:00:00"
  goldenWindowMinutes: number;
  lotCapacity: number;
  minSubjectDurationMinutes: number;
  maxSubjectDurationMinutes: number;
  /** A maximum heart rate above this raises an alert. */
  maxHeartRateLimit: number;
  /** A recovery heart rate above this raises an alert. */
  recoveryHeartRateLimit: number;
}

export interface ReadinessAssessment {
  id: number;
  horseId: number;
  readinessStatus: string;
  fitnessScore: number | null;
  conformationScore: number | null;
  temperamentScore: number | null;
  gaitQualityScore: number | null;
  estimatedMonthsToRace: number | null;
  assessmentDate: string;
}

