package com.rtms.backend.racing.service;

import com.rtms.backend.config.ApiException;
import com.rtms.backend.racing.dto.CreateRaceRegistrationRequest;
import com.rtms.backend.racing.dto.RaceRegistrationResponse;
import com.rtms.backend.racing.dto.ReviewRaceRegistrationRequest;
import com.rtms.backend.stable.entity.Area;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.racing.entity.RaceRegistration;
import com.rtms.backend.stable.entity.StableStall;
import com.rtms.backend.horse.enums.HorseStatus;
import com.rtms.backend.racing.enums.RaceRegistrationStatus;
import com.rtms.backend.stable.repository.AreaRepository;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.racing.repository.RaceRegistrationRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RaceRegistrationService {

    private final RaceRegistrationRepository raceRegistrationRepository;
    private final HorseRepository horseRepository;
    private final StableStallRepository stableStallRepository;
    private final AreaRepository areaRepository;

    public RaceRegistrationService(
            RaceRegistrationRepository raceRegistrationRepository,
            HorseRepository horseRepository,
            StableStallRepository stableStallRepository,
            AreaRepository areaRepository) {
        this.raceRegistrationRepository = raceRegistrationRepository;
        this.horseRepository = horseRepository;
        this.stableStallRepository = stableStallRepository;
        this.areaRepository = areaRepository;
    }

    @Transactional
    public RaceRegistrationResponse create(CreateRaceRegistrationRequest request, AuthenticatedUser currentUser) {
        Long trainerId = currentUser.getUserId();
        Horse horse = requireManagedHorse(request.getHorseId(), trainerId);

        if (request.getEventDate().isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_DATE",
                    "Race date must not be in the past");
        }

        if (request.getNominationDeadline() != null) {
            if (request.getNominationDeadline().isAfter(request.getEventDate())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE",
                        "Registration deadline must not be after the race date");
            }
        }

        String sourceUrl = null;
        if (request.getSourceUrl() != null && !request.getSourceUrl().isBlank()) {
            sourceUrl = request.getSourceUrl().trim();
            if (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_URL",
                        "Race source link must be a valid HTTP or HTTPS URL");
            }
        }

        RaceRegistration reg = new RaceRegistration();
        reg.setHorseId(horse.getId());
        reg.setTrainerId(trainerId);
        reg.setRaceName(request.getRaceName().trim());
        reg.setRaceCategory(request.getRaceCategory().trim());
        reg.setLocation(request.getLocation().trim());
        reg.setEventDate(request.getEventDate());
        reg.setEventTime(request.getEventTime());
        reg.setOrganizer(request.getOrganizer() != null ? request.getOrganizer().trim() : null);
        reg.setSourceUrl(sourceUrl);
        reg.setNominationDeadline(request.getNominationDeadline());
        reg.setDistanceMeters(request.getDistanceMeters());
        reg.setTrackType(request.getTrackType() != null ? request.getTrackType().trim() : null);
        reg.setPrizeDetails(request.getPrizeDetails() != null ? request.getPrizeDetails().trim() : null);
        reg.setSelectionReason(request.getSelectionReason().trim());
        reg.setTrainerNotes(request.getTrainerNotes() != null ? request.getTrainerNotes().trim() : null);
        reg.setStatus(RaceRegistrationStatus.PENDING);

        RaceRegistration saved = raceRegistrationRepository.save(reg);
        return new RaceRegistrationResponse(saved, horse.getName(), horse.getRegistrationNumber());
    }

    public List<RaceRegistrationResponse> listVisible(AuthenticatedUser user, Long horseId) {
        boolean isManager = "CLUB_MANAGER".equalsIgnoreCase(user.getRole()) || "ADMIN".equalsIgnoreCase(user.getRole());
        List<RaceRegistration> list;
        if (isManager) {
            if (horseId != null) {
                list = raceRegistrationRepository.findByHorseIdOrderByCreatedAtDesc(horseId);
            } else {
                list = raceRegistrationRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            if (horseId != null) {
                list = raceRegistrationRepository.findByTrainerIdAndHorseIdOrderByCreatedAtDesc(user.getUserId(), horseId);
            } else {
                list = raceRegistrationRepository.findByTrainerIdOrderByCreatedAtDesc(user.getUserId());
            }
        }

        if (list.isEmpty()) {
            return List.of();
        }

        Set<Long> horseIds = list.stream().map(RaceRegistration::getHorseId).collect(Collectors.toSet());
        Map<Long, Horse> horseMap = horseRepository.findAllById(horseIds).stream()
                .collect(Collectors.toMap(Horse::getId, Function.identity(), (a, b) -> a));

        return list.stream().map(reg -> {
            Horse h = horseMap.get(reg.getHorseId());
            String horseName = h != null ? h.getName() : null;
            String horseRegNum = h != null ? h.getRegistrationNumber() : null;
            return new RaceRegistrationResponse(reg, horseName, horseRegNum);
        }).toList();
    }

    public RaceRegistrationResponse getVisible(Long id, AuthenticatedUser user) {
        RaceRegistration reg = raceRegistrationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                        "Race nomination #" + id + " not found"));

        boolean isManager = "CLUB_MANAGER".equalsIgnoreCase(user.getRole()) || "ADMIN".equalsIgnoreCase(user.getRole());
        if (!isManager && !reg.getTrainerId().equals(user.getUserId())) {
            throw new AccessDeniedException("You do not have permission to view this nomination");
        }

        Horse horse = horseRepository.findById(reg.getHorseId()).orElse(null);
        String horseName = horse != null ? horse.getName() : null;
        String horseRegNum = horse != null ? horse.getRegistrationNumber() : null;
        return new RaceRegistrationResponse(reg, horseName, horseRegNum);
    }

    @Transactional
    public RaceRegistrationResponse review(Long id, ReviewRaceRegistrationRequest request, AuthenticatedUser user) {
        RaceRegistration registration = raceRegistrationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                        "Race nomination #" + id + " not found"));

        if (registration.getStatus() != RaceRegistrationStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_REVIEWED",
                    "This nomination has already been reviewed");
        }

        RaceRegistrationStatus status = request.getStatus();
        if (status != RaceRegistrationStatus.APPROVED && status != RaceRegistrationStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS",
                    "Status must be APPROVED or REJECTED");
        }

        registration.setStatus(status);
        registration.setManagerFeedback(request.getManagerFeedback());
        registration.setReviewedById(user.getUserId());
        registration.setReviewedAt(LocalDateTime.now());

        RaceRegistration saved = raceRegistrationRepository.save(registration);
        Horse horse = horseRepository.findById(saved.getHorseId()).orElse(null);
        return new RaceRegistrationResponse(saved, horse != null ? horse.getName() : null,
                horse != null ? horse.getRegistrationNumber() : null);
    }

    private Horse requireManagedHorse(Long horseId, Long trainerId) {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Horse not found"));
        if (horse.getCurrentStallId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HORSE_NOT_ASSIGNED",
                    "The horse has not been assigned to a stall");
        }
        StableStall stall = stableStallRepository.findById(horse.getCurrentStallId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "INVALID_STALL", "The horse stall does not exist"));
        Area area = areaRepository.findById(stall.getAreaId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "INVALID_AREA", "The horse area does not exist"));
        if (!trainerId.equals(area.getTrainerId())) {
            throw new AccessDeniedException("The horse is not in an area you manage");
        }
        if (!horse.canTrain()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HORSE_NOT_ELIGIBLE",
                    "The horse cannot be nominated for a race right now");
        }
        return horse;
    }
}
