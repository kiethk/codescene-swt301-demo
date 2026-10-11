'use client';

import { useState, type ReactNode } from 'react';
import { Sidebar } from '@/components/layout/Sidebar';
import { TopBar } from '@/components/layout/TopBar';
import {
  Avatar,
  Button,
  Checkbox,
  ConfirmDialog,
  EmptyState,
  FieldLabel,
  FilterChips,
  FormField,
  IconButton,
  Input,
  LinkButton,
  ListSkeleton,
  MetricCard,
  Modal,
  Notice,
  Panel,
  Pill,
  ProgressBar,
  SearchInput,
  SectionTitle,
  SegmentedControl,
  SelectableCard,
  Select,
  Skeleton,
  Spinner,
  Table,
  TBody,
  Td,
  Textarea,
  Th,
  THead,
  Tooltip,
  Tr,
} from '@/components/ui';
import { getNavigationForRole } from '@/config/navigation';
import { ROLE_LABELS } from '@/lib/roleRoute';
import { toast } from '@/lib/toast';
import type { Role } from '@/types/auth';

const ROLES = Object.keys(ROLE_LABELS) as Role[];

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <SectionTitle>{title}</SectionTitle>
      {children}
    </section>
  );
}

/** Living reference for every shared primitive. Add new primitives here. */
export default function DesignTestPage() {
  const [role, setRole] = useState<Role>('HEAD_TRAINER');
  const [selectedCard, setSelectedCard] = useState<'a' | 'b'>('a');
  const [collapsed, setCollapsed] = useState(false);
  const [view, setView] = useState<'week' | 'month'>('week');
  const [status, setStatus] = useState<'ALL' | 'ACTIVE' | 'DONE'>('ALL');
  const [search, setSearch] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  return (
    <div className="mx-auto max-w-5xl space-y-10 p-8">
      <h1 className="text-2xl font-semibold">RTMS design system</h1>

      <Section title="App shell">
        <FilterChips
          label="Preview role"
          value={role}
          onChange={setRole}
          options={ROLES.map((value) => ({ value, label: ROLE_LABELS[value] }))}
        />
        <div className="flex h-[30rem] overflow-hidden rounded-[var(--radius-lg)] border border-[var(--color-border)] [&>aside]:h-full">
          <Sidebar sections={getNavigationForRole(role)} role={role} collapsed={collapsed} />
          <div className="flex min-w-0 flex-1 flex-col bg-[var(--color-background)]">
            <TopBar
              title="Dashboard"
              sidebarCollapsed={collapsed}
              canToggleSidebar
              onToggleSidebar={() => setCollapsed((value) => !value)}
            />
            <div className="p-6 text-sm text-[var(--color-text-secondary)]">Page content</div>
          </div>
        </div>
      </Section>

      <Section title="Buttons">
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="primary">Primary</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="tertiary">Tertiary</Button>
          <Button variant="destructive">Destructive</Button>
          <Button variant="warning">Warning</Button>
          <Button variant="link">Link</Button>
          <Button variant="primary" loading>Loading</Button>
          <Button variant="primary" disabled>Disabled</Button>
          <Button variant="primary" icon="plus" size="sm">Small</Button>
          <LinkButton href="/design-test" variant="secondary" iconRight="chevron-right">LinkButton</LinkButton>
          <IconButton icon="refresh" label="Refresh" />
          <IconButton icon="x" label="Close" size="sm" />
        </div>
      </Section>

      <Section title="Form controls">
        <Panel padded className="grid gap-4 sm:grid-cols-2">
          <FormField label="Horse name" required hint="As written on the passport.">
            <Input placeholder="e.g. Northern Star" />
          </FormField>
          <FormField label="Microchip" error="Microchip must be 15 digits.">
            <Input defaultValue="1234" />
          </FormField>
          <FormField label="Breed">
            <Select defaultValue="">
              <option value="" disabled>Select a breed</option>
              <option>Thoroughbred</option>
              <option>Arabian</option>
            </Select>
          </FormField>
          <FormField label="Search">
            <SearchInput value={search} onChange={setSearch} placeholder="Search horses" />
          </FormField>
          <FormField label="Notes" className="sm:col-span-2">
            <Textarea placeholder="Anything the vet should know" />
          </FormField>
          <Checkbox label="Isolate on arrival" description="Keeps the horse out of shared paddocks." />
          <Checkbox type="radio" name="demo" label="Radio option" />
        </Panel>
      </Section>

      <Section title="Segmented control and filter chips">
        <div className="flex flex-wrap items-center gap-4">
          <SegmentedControl
            label="Calendar view"
            value={view}
            onChange={setView}
            options={[
              { value: 'week', label: 'Week', icon: 'list' },
              { value: 'month', label: 'Month', icon: 'grid' },
            ]}
          />
          <FilterChips
            label="Status"
            value={status}
            onChange={setStatus}
            options={[
              { value: 'ALL', label: 'All', count: 12 },
              { value: 'ACTIVE', label: 'Active', count: 8 },
              { value: 'DONE', label: 'Completed', count: 4 },
            ]}
          />
        </div>
      </Section>

      <Section title="Panels and metrics">
        <div className="grid gap-4 sm:grid-cols-3">
          <Panel padded>
            <FieldLabel>Default panel</FieldLabel>
            <p className="mt-1 text-sm text-[var(--color-text-secondary)]">Radius, border and shadow from tokens.</p>
          </Panel>
          <Panel padded tone="warning">
            <FieldLabel>Tone: warning</FieldLabel>
            <p className="mt-1 text-sm text-[var(--color-text-secondary)]">For a card that needs attention.</p>
          </Panel>
          <Panel padded interactive>
            <FieldLabel>Interactive</FieldLabel>
            <p className="mt-1 text-sm text-[var(--color-text-secondary)]">Lifts on hover.</p>
          </Panel>
          <MetricCard label="Horses in training" value={42} icon="horse" />
          <SelectableCard selected={selectedCard === 'a'} onClick={() => setSelectedCard('a')}>
            <FieldLabel>SelectableCard</FieldLabel>
            <p className="mt-1 text-sm text-[var(--color-text-secondary)]">Picked from a set; click to select.</p>
          </SelectableCard>
          <SelectableCard dimmed empty selected={selectedCard === 'b'} onClick={() => setSelectedCard('b')}>
            <FieldLabel>Dimmed, empty</FieldLabel>
            <p className="mt-1 text-sm text-[var(--color-text-secondary)]">Outside the viewer&apos;s scope.</p>
          </SelectableCard>
          <MetricCard label="Avg fitness" value="7.8" unit="/10" tone="success" icon="activity" />
          <MetricCard label="Open incidents" value={3} tone="danger" icon="alert-triangle" hint="2 need a vet" />
        </div>
      </Section>

      <Section title="Progress bar">
        <Panel padded className="space-y-4">
          <div className="space-y-1.5">
            <FieldLabel>Single value</FieldLabel>
            <ProgressBar label="Course progress" value={7} max={12} />
            <ProgressBar label="Course progress, small" value={9} max={12} size="sm" tone="success" />
          </div>
          <div className="space-y-1.5">
            <FieldLabel>Stacked distribution</FieldLabel>
            <ProgressBar
              label="Team performance distribution"
              max={10}
              segments={[
                { value: 4, tone: 'success', label: 'Excellent: 4' },
                { value: 3, tone: 'info', label: 'On target: 3' },
                { value: 2, tone: 'warning', label: 'Needs improvement: 2' },
                { value: 1, tone: 'neutral', label: 'No data: 1' },
              ]}
            />
          </div>
        </Panel>
      </Section>

      <Section title="Badges, avatars, tooltips">
        <div className="flex flex-wrap items-center gap-3">
          <Pill tone="success">Eligible</Pill>
          <Pill tone="warning">Pending</Pill>
          <Pill tone="danger">Rejected</Pill>
          <Pill tone="info" icon="clipboard">Candidate</Pill>
          <Pill tone="isolated">Isolated</Pill>
          <Pill tone="primary">Primary</Pill>
          <Pill tone="neutral" size="sm">Neutral</Pill>
          <Avatar name="Test Trainer" />
          <Tooltip content="Tooltip content">
            <Button variant="secondary">Hover me</Button>
          </Tooltip>
        </div>
      </Section>

      <Section title="Notices and toasts">
        <div className="space-y-2">
          <Notice tone="success">Admission approved.</Notice>
          <Notice tone="error" title="Could not save">The server rejected the request.</Notice>
          <Notice tone="warning">This plan overlaps another lot.</Notice>
          <Notice tone="info" onDismiss={() => undefined}>Dismissible notice.</Notice>
          <div className="flex flex-wrap gap-2 pt-1">
            <Button onClick={() => toast.success('Status updated')}>Success toast</Button>
            <Button onClick={() => toast.error('Unable to cancel this lot', 'Try again in a moment.')}>
              Error toast
            </Button>
          </div>
        </div>
      </Section>

      <Section title="Overlays">
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => setModalOpen(true)}>Open modal</Button>
          <Button onClick={() => setConfirmOpen(true)}>Open confirm</Button>
        </div>
        <Modal
          open={modalOpen}
          onClose={() => setModalOpen(false)}
          title="Reschedule workout"
          description="Pick a new time slot for this lot."
          footer={
            <>
              <Button onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button variant="primary" onClick={() => setModalOpen(false)}>Save</Button>
            </>
          }
        >
          <FormField label="New date">
            <Input type="date" />
          </FormField>
        </Modal>
        <ConfirmDialog
          open={confirmOpen}
          title="Cancel this lot?"
          description="Riders will be notified. This cannot be undone."
          tone="danger"
          confirmLabel="Cancel lot"
          cancelLabel="Keep"
          onConfirm={() => setConfirmOpen(false)}
          onCancel={() => setConfirmOpen(false)}
        />
      </Section>

      <Section title="Table">
        <Table>
          <THead>
            <Tr>
              <Th>Horse</Th>
              <Th>Status</Th>
              <Th className="text-right">Fitness</Th>
            </Tr>
          </THead>
          <TBody>
            {['Northern Star', 'Silver Arrow', 'Night Runner'].map((name, index) => (
              <Tr key={name} interactive>
                <Td className="font-medium">{name}</Td>
                <Td><Pill tone={index === 1 ? 'warning' : 'success'}>{index === 1 ? 'Resting' : 'Training'}</Pill></Td>
                <Td className="font-metric text-right">{(7.2 + index * 0.4).toFixed(1)}</Td>
              </Tr>
            ))}
          </TBody>
        </Table>
      </Section>

      <Section title="Loading and empty states">
        <div className="grid gap-4 sm:grid-cols-2">
          <Panel>
            <ListSkeleton rows={3} />
          </Panel>
          <Panel padded className="space-y-3">
            <div className="flex items-center gap-3">
              <Spinner size="sm" />
              <Spinner />
              <Spinner size="lg" />
            </div>
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-20 w-full" />
          </Panel>
          <Panel className="sm:col-span-2">
            <EmptyState
              icon="search"
              title="No records found"
              description="We couldn't find any data matching your criteria."
              action={<Button variant="primary">Create new</Button>}
            />
          </Panel>
        </div>
      </Section>
    </div>
  );
}
