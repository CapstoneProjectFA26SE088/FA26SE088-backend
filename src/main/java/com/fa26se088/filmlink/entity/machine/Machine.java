package com.fa26se088.filmlink.entity.machine;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.machine.enums.MachineStatus;
import com.fa26se088.filmlink.entity.machine.enums.MachineType;
import com.fa26se088.filmlink.entity.masterdata.FilmFormat;
import com.fa26se088.filmlink.entity.masterdata.FilmProcess;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Thiết bị máy tráng (PROCESSOR) hoặc máy scan (SCANNER) của Film Lab (UC68, bảng {@code
 * machines}).
 */
@Getter
@Setter
@Entity
@Table(name = "machines")
public class Machine extends BaseEntity {

  @Column(name = "film_lab_id", nullable = false)
  private Long filmLabId;

  @Column(name = "machine_code", nullable = false, length = 50)
  private String machineCode;

  @Column(name = "model_name", length = 150)
  private String modelName;

  @Enumerated(EnumType.STRING)
  @Column(name = "machine_type", nullable = false, length = 20)
  private MachineType machineType;

  @Column(name = "capacity_per_hour", nullable = false)
  private Integer capacityPerHour;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private MachineStatus status = MachineStatus.ACTIVE;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "machine_film_processes",
      joinColumns = @JoinColumn(name = "machine_id"),
      inverseJoinColumns = @JoinColumn(name = "film_process_id"))
  private Set<FilmProcess> filmProcesses = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "machine_film_formats",
      joinColumns = @JoinColumn(name = "machine_id"),
      inverseJoinColumns = @JoinColumn(name = "film_format_id"))
  private Set<FilmFormat> filmFormats = new HashSet<>();

  @OneToMany(mappedBy = "machine", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<MachineWorkingHour> workingHours = new ArrayList<>();

  /** Thêm một dòng cấu hình giờ hoạt động cho máy. */
  public void addWorkingHour(MachineWorkingHour workingHour) {
    workingHours.add(workingHour);
    workingHour.setMachine(this);
  }
}
