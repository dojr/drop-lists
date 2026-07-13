package com.droplists;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DropList
{
	private String id;
	private String name;
	private List<Integer> itemIds = new ArrayList<>();
	private boolean enabled;
}
