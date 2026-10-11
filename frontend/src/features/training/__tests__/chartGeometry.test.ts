import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { CHART_PADDING, PLOT_HEIGHT, PLOT_WIDTH, labelStride, linePath, seriesPoints, xAt, xAtTime, yAt } from '../lib/chartGeometry.ts';

describe('chart geometry', () => {
  it('spreads points evenly from the left to the right edge of the plot', () => {
    assert.equal(xAt(0, 5), CHART_PADDING.left);
    assert.equal(xAt(4, 5), CHART_PADDING.left + PLOT_WIDTH);
  });

  it('centres a lone point', () => {
    assert.equal(xAt(0, 1), CHART_PADDING.left + PLOT_WIDTH / 2);
  });

  it('puts the minimum at the bottom and the maximum at the top, clamping values outside the axis', () => {
    assert.equal(yAt(0, 0, 10), CHART_PADDING.top + PLOT_HEIGHT);
    assert.equal(yAt(10, 0, 10), CHART_PADDING.top);
    assert.equal(yAt(99, 0, 10), CHART_PADDING.top);
    assert.equal(yAt(null, 0, 10), null);
  });

  it('skips sessions without a value but keeps their place on the x axis', () => {
    const points = seriesPoints([5, null, 9], 0, 10);
    assert.equal(points.length, 2);
    assert.equal(points[1][0], xAt(2, 3));
  });

  it('builds a path, and a short segment for one point', () => {
    assert.equal(linePath([]), '');
    assert.equal(linePath([[10, 20], [30, 40]]), 'M 10 20 L 30 40');
    assert.equal(linePath([[10, 20]]), 'M 10 20 L 10.1 20');
  });

  it('places a date on the time axis and clamps it', () => {
    assert.equal(xAtTime(0, 0, 100), CHART_PADDING.left);
    assert.equal(xAtTime(500, 0, 100), CHART_PADDING.left + PLOT_WIDTH);
    assert.equal(xAtTime(5, 5, 5), CHART_PADDING.left + PLOT_WIDTH / 2);
  });

  it('thins out date labels only when there are many sessions', () => {
    assert.equal(labelStride(12), 1);
    assert.equal(labelStride(30), 3);
  });
});
