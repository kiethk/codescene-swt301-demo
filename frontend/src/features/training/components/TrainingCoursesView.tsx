'use client';

import { useState } from 'react';
import { PageHeader, ScreenLayout, Tabs } from '@/components/ui';
import { CourseList } from './CourseList';
import { SubjectList } from './SubjectList';

type TrainingTab = 'courses' | 'subjects';

export function TrainingCoursesView() {
  const [tab, setTab] = useState<TrainingTab>('courses');

  return (
    <ScreenLayout variant="list">
      <PageHeader
        title="Training"
        description="Build exercises, combine them into courses, and enroll horses."
      />
      <Tabs
        tabs={[
          { id: 'courses', label: 'Training courses', icon: 'activity' },
          { id: 'subjects', label: 'Exercise library', icon: 'clipboard' },
        ]}
        active={tab}
        onChange={(id) => setTab(id as TrainingTab)}
      />
      {tab === 'courses' ? <CourseList /> : <SubjectList />}
    </ScreenLayout>
  );
}
