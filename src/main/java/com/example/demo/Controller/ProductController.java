package com.example.demo.Controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.CustomMultipartFile;
import com.example.demo.Dto.CartItemDto;
import com.example.demo.Dto.ProductDto;
import com.example.demo.Dto.SpecificationDTO;
import com.example.demo.Dto.SpecificationOptionDTO;
import com.example.demo.Imagekit.ImagekitService;
import com.example.demo.Repository.ClickedProductHistoryRepository;
import com.example.demo.Repository.ProductRepository;
import com.example.demo.Repository.SpecificationRepository;
import com.example.demo.Repository.SubcategoryRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.Service.CartService;
import com.example.demo.Service.CategoryService;
import com.example.demo.Service.ProductService;
import com.example.demo.Service.SpecificationOptionService;
import com.example.demo.Service.SpecificationService;
import com.example.demo.Service.SubcategoryService;
import com.example.demo.model.ImageInfo;
import com.example.demo.model.Product;
import com.example.demo.model.Specification;
import com.example.demo.model.SpecificationOption;
import com.example.demo.model.Subcategory;
import com.example.demo.model.UserEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.imagekit.sdk.exceptions.BadRequestException;
import io.imagekit.sdk.exceptions.ForbiddenException;
import io.imagekit.sdk.exceptions.InternalServerException;
import io.imagekit.sdk.exceptions.TooManyRequestsException;
import io.imagekit.sdk.exceptions.UnauthorizedException;
import io.imagekit.sdk.exceptions.UnknownException;
import jakarta.transaction.Transactional;
import net.coobird.thumbnailator.Thumbnails;

@RestController
@RequestMapping("/api")
public class ProductController {

	@Autowired
	private SubcategoryService subcategoryService;

	@Autowired
	private SubcategoryRepository subcategoryRepository;

	@Autowired
	private ProductService productService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private SpecificationService specificationService;

	@Autowired
	private SpecificationOptionService specificationOptionService;

	@Autowired
	private SpecificationRepository specificationRepository;

	@Autowired
	private ImagekitService imagekitService;

	@Autowired
	private ClickedProductHistoryRepository clickedProductHistoryRepository;

	@Autowired
	private CartService cartService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProductRepository productRepository;

	@PostMapping("/{subcategoryId}/addProduct")
	public ResponseEntity<ProductDto> addProductToSubcategory1(@PathVariable("subcategoryId") Long subcategoryId,
			@RequestParam("name") String name, @RequestParam("description") String description, @RequestParam("baseprice") String basePriceStr, // Changed
																															// from
																															// Double
																															// to
																															// String
			@RequestParam("minOrderQuantity") Integer minOrderQuantity, @RequestParam("maxQuantity") Integer maxQuantity,
			@RequestParam("incrementStep") Integer incrementStep, @RequestParam("images") List<MultipartFile> images,
			@RequestParam("specificationsJson") String specificationsJson, @RequestParam("specImages") List<MultipartFile> specImages)
			throws IOException, InternalServerException, BadRequestException, UnknownException, ForbiddenException,
			TooManyRequestsException, UnauthorizedException {

		// Validate price input
		BigDecimal basePrice;
		try {
			basePrice = new BigDecimal(basePriceStr).setScale(2, RoundingMode.HALF_UP);
		} catch (NumberFormatException e) {
			throw new BadRequestException("Invalid price format", e, false, false, specificationsJson,
					specificationsJson, null);
		}

		// Validate if subcategory exists
		Subcategory subcategory = subcategoryRepository.findById(subcategoryId)
				.orElseThrow(() -> new RuntimeException("Subcategory not found"));

		// Upload product images and convert to ImageInfo list
		List<ImageInfo> imageInfos = images.stream()
			    .filter(image -> !image.isEmpty())
			    .map(image -> {
			        try {
			            // ✅ Compress the image before uploading
			            MultipartFile compressedImage = compressImage(image);

			            String url = imagekitService.uploadFileToProduct(compressedImage);

			            ImageInfo info = new ImageInfo();
			            info.setUrl(url);
			            return info;
			        } catch (Exception e) {
			            throw new RuntimeException("Failed to upload image", e);
			        }
			    })
			    .collect(Collectors.toList());





		// Create and save the product
		Product product = new Product();
		product.setName(name);
		product.setDescription(description);
		product.setBaseprice(basePrice); // Using BigDecimal
		product.setMinOrderquantity(minOrderQuantity);
		product.setMaxQuantity(maxQuantity);
		product.setIncrementStep(incrementStep);
		product.setEncryptedImages(imageInfos); // ✅ Use List<ImageInfo>
		product.setSubcategory(subcategory);
		product.setCategory(subcategory.getCategory());
		// Set it
		product = productService.saveProduct(product);

		// Parse and save specifications
		ObjectMapper mapper = new ObjectMapper();
		List<SpecificationDTO> specifications = mapper.readValue(specificationsJson,
				new TypeReference<List<SpecificationDTO>>() {
				});

		for (SpecificationDTO specDto : specifications) {
			Specification specification = new Specification();
			specification.setName(specDto.getName());
			specification.setProduct(product);
			specification = specificationService.saveSpecification(specification);

			int optionIndex = 0;
			for (SpecificationOptionDTO optionDto : specDto.getOptions()) {
				SpecificationOption option = new SpecificationOption();
				option.setName(optionDto.getName());

				// Convert option price to BigDecimal
				BigDecimal optionPrice;
				try {
					optionPrice = new BigDecimal(optionDto.getPrice().toString()).setScale(2, RoundingMode.HALF_UP);
				} catch (Exception e) {
					throw new BadRequestException("Invalid option price format", e, false, false, specificationsJson,
							specificationsJson, null);
				}
				option.setPrice(optionPrice);

				if (optionIndex < specImages.size()) {
					MultipartFile specImage = specImages.get(optionIndex);
					if (specImage != null && !specImage.isEmpty()) {
					    MultipartFile compressedSpecImage = compressImage(specImage);

					    String specImageUrl = imagekitService.uploadSpecificationImageFile(compressedSpecImage);

					    ImageInfo imageInfo = new ImageInfo();
					    imageInfo.setUrl(specImageUrl);

					    option.setImage(imageInfo);
					}

				}

				option.setSpecification(specification);
				specificationOptionService.saveSpecificationOption(option);
				optionIndex++;
			}
		}

		// Convert to DTO
		ProductDto productDto = convertToProductDto(product);

		// Fetch specifications for response
		List<Specification> productSpecs = specificationService.getSpecificationsByProduct(product);
		List<SpecificationDTO> specDtos = productSpecs.stream().map(spec -> {
			SpecificationDTO dto = new SpecificationDTO();
			dto.setId(spec.getId());
			dto.setName(spec.getName());

			List<SpecificationOptionDTO> optionDtos = specificationOptionService
					.getSpecificationOptionsBySpecification(spec).stream()
					.map(opt -> new SpecificationOptionDTO(opt.getId(), opt.getName(), opt.getImage(), opt.getPrice()))
					.collect(Collectors.toList());

			dto.setOptions(optionDtos);
			return dto;
		}).collect(Collectors.toList());

		productDto.setSpecifications(specDtos);

		return new ResponseEntity<>(productDto, HttpStatus.CREATED);
	}

	// ================================================//
	@PostMapping("/calculateTotalPrice")
	public ResponseEntity<BigDecimal> calculateTotalPrice(@RequestParam("productId") Long productId,
			@RequestParam("selectedQuantity") Integer selectedQuantity, @RequestBody List<Long> selectedSpecificationIds) {
		try {
			// Call the service to calculate the total price
			BigDecimal totalPrice = productService.calculateTotalPrice(productId, selectedQuantity,
					selectedSpecificationIds);

			// Return the calculated total price
			return ResponseEntity.ok(totalPrice);
		} catch (RuntimeException e) {
			// Handle errors and return a bad request with the error message
			return ResponseEntity.badRequest().body(null);
		}
	}

	// Helper method
	private ProductDto convertToProductDto(Product product) {
		ProductDto dto = new ProductDto();
		dto.setId(product.getId());
		dto.setName(product.getName());
		dto.setDescription(product.getDescription());
		dto.setBasePrice(product.getBaseprice());
		dto.setMinOrderQuantity(product.getMinOrderquantity());
		dto.setMaxQuantity(product.getMaxQuantity());
		dto.setIncrementStep(product.getIncrementStep());
		dto.setEncryptedImages(product.getEncryptedImages());
		dto.setSubcategoryId(product.getSubcategory().getId());
		dto.setCategoryId(product.getCategory().getId());
		return dto;
	}

	@GetMapping("/CalculateSubtotal")
	public ResponseEntity<BigDecimal> getCartSubtotal(@RequestParam(name = "sessionId", required = false) String sessionId,
			@AuthenticationPrincipal UserDetails userDetails) {

		System.out.println("🔐 Authenticated User: " + (userDetails != null ? userDetails.getUsername() : "null"));

		try {
			BigDecimal subtotal;

			if (userDetails != null) {
				String userEmail = userDetails.getUsername();
				UserEntity user = userRepository.findByEmail(userEmail)
						.orElseThrow(() -> new RuntimeException("User not found"));
				subtotal = getSubtotalForUser(user);

			} else if (sessionId != null && !sessionId.isEmpty()) {
				subtotal = getSubtotalForSession(sessionId);

			} else {
				return ResponseEntity.badRequest().body(BigDecimal.ZERO);
			}

			return ResponseEntity.ok(subtotal.setScale(2, RoundingMode.HALF_UP));

		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BigDecimal.ZERO);
		}
	}

	@GetMapping("/RetrieveProduct/{productId}")
	public ResponseEntity<ProductDto> getProductById(@PathVariable("productId") Long productId) {
		try {
			// Call the service method that increments views and returns ProductDto already
			// mapped
			ProductDto productDto = productService.getProductAndIncrementViews(productId);

			// Now fetch specifications separately and set them into productDto
			List<Specification> specifications = specificationRepository.findByProductId(productId);

			List<SpecificationDTO> specificationDtos = specifications.stream().map(spec -> {
				SpecificationDTO specDto = new SpecificationDTO();
				specDto.setId(spec.getId());
				specDto.setName(spec.getName());
				specDto.setOptions(spec.getOptions().stream().map(option -> {
					SpecificationOptionDTO optionDto = new SpecificationOptionDTO();
					optionDto.setId(option.getId());
					optionDto.setName(option.getName());
					optionDto.setPrice(option.getPrice());
					optionDto.setImage(option.getImage());
					return optionDto;
				}).collect(Collectors.toList()));
				return specDto;
			}).collect(Collectors.toList());

			productDto.setSpecifications(specificationDtos);

			return ResponseEntity.ok(productDto);
		} catch (RuntimeException e) {
			// product not found or other runtime exception
			return ResponseEntity.notFound().build();
		}
	}

	// Display product based on sub-category
	@GetMapping("/{categoryId}/subcategories/{subcategoryId}/products")
	public ResponseEntity<?> getProductsBySubcategoryAndCategory(@PathVariable("categoryId") Long categoryId,
			@PathVariable("subcategoryId") Long subcategoryId) {

		try {
			// Fetch products by subcategory and category, returning DTOs
			List<ProductDto> products = categoryService.getProductsBySubcategoryAndCategory(categoryId, subcategoryId);
			return ResponseEntity.ok(products);
		} catch (IllegalArgumentException e) {
			// Handle errors if category or subcategory not found
			return ResponseEntity.status(404).body(e.getMessage());
		}
	}


	@GetMapping("/products/{id}/similar")
	public ResponseEntity<?> getSimilarProducts(@PathVariable("id") Long id, @RequestParam(name = "sessionId", required = false) String sessionId,
			@RequestParam(name = "page",defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size, // default
																														// size
																														// 10
			Principal principal) {
		if (principal == null && sessionId == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized: Provide sessionId or JWT");
		}

		try {
			Page<ProductDto> similarProducts = productService.getSimilarProducts(id, page, size);
			return ResponseEntity.ok(similarProducts);
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
		}
	}

	private BigDecimal getSubtotalForSession(String sessionId) {
		List<CartItemDto> cartItems = cartService.getAllCartItems(sessionId);
		return productService.calculateCartSubtotal(cartItems);
	}

	private BigDecimal getSubtotalForUser(UserEntity user) {
		List<CartItemDto> cartItems = cartService.getAllCartItemsByUser(user);
		return productService.calculateCartSubtotal(cartItems);
	}

	@GetMapping("/trending")
	public ResponseEntity<?> getTrendingProducts(@RequestParam(name = "sessionId", required = false) String sessionId,
			Principal principal) {

		// Check authorization: either Principal (JWT) or sessionId must be present
		if (principal == null && (sessionId == null || sessionId.isEmpty())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized: Provide sessionId or JWT");
		}

		// You can add extra logic here if you want to check session validity or
		// Principal details

		List<ProductDto> trendingProducts = productService.getTrendingProducts();
		return ResponseEntity.ok(trendingProducts);
	}

	@GetMapping("/search")
	public ResponseEntity<?> searchProducts(@RequestParam("name") String name, @RequestParam(name = "sessionId", required = false) String sessionId,

			Principal principal) {
		// Allow either JWT (via Principal) or sessionId for access
		if (principal == null && sessionId == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized: Provide sessionId or JWT");
		}

		// Fetch products by name (no pagination, no sort)
		List<ProductDto> products = productService.searchProductsByName(name);

		return ResponseEntity.ok(products);
	}

	@Transactional
	@DeleteMapping("/products/{id}/soft-delete")
	public ResponseEntity<String> softDeleteProduct(@PathVariable("id") Long id) {
	    Optional<Product> productOptional = productRepository.findById(id);

	    if (productOptional.isEmpty()) {
	        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
	    }

	    Product product = productOptional.get();
	    product.setIsDeleted(true);
	    productRepository.save(product);

	    return ResponseEntity.ok("Product soft deleted successfully");
	}


	@DeleteMapping("/products/permanent/{productId}")
	public ResponseEntity<String> deleteProductPermanently(@PathVariable("productId") Long productId) {
	    try {
	        productService.deleteProductPermanently(productId);
	        return ResponseEntity.ok("Product permanently deleted.");
	    } catch (IllegalArgumentException e) {
	        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting product.");
	    }
	}
	//====================================================================//

	@PutMapping("/products/{productId}/update")
	public ResponseEntity<ProductDto> updateProduct(
	        @PathVariable Long productId,
	        @RequestParam(required = false) String name,
	        @RequestParam(required = false) String description,
	        @RequestParam(required = false) String baseprice,
	        @RequestParam(required = false) Integer minOrderQuantity,
	        @RequestParam(required = false) Integer maxQuantity,
	        @RequestParam(required = false) Integer incrementStep,
	        @RequestParam(required = false) List<MultipartFile> images,
	        @RequestParam(required = false) String specificationsJson,
	        @RequestParam(required = false) List<MultipartFile> specImages
	) throws Exception {
	    Product product = productRepository.findById(productId)
	            .orElseThrow(() -> new RuntimeException("Product not found"));

	    // --- Update fields if present ---
	    if (name != null) {
			product.setName(name);
		}
	    if (description != null) {
			product.setDescription(description);
		}
	    if (baseprice != null) {
	        try {
	            BigDecimal price = new BigDecimal(baseprice).setScale(2, RoundingMode.HALF_UP);
	            product.setBaseprice(price);
	        } catch (NumberFormatException e) {
	            throw new BadRequestException("Invalid price", e, false, false, baseprice, baseprice, null);
	        }
	    }
	    if (minOrderQuantity != null) {
			product.setMinOrderquantity(minOrderQuantity);
		}
	    if (maxQuantity != null) {
			product.setMaxQuantity(maxQuantity);
		}
	    if (incrementStep != null) {
			product.setIncrementStep(incrementStep);
		}

	    // --- Update images if uploaded ---
	    if (images != null && !images.isEmpty()) {
	        List<ImageInfo> imageInfos = images.stream()
	                .filter(image -> !image.isEmpty())
	                .map(image -> {
	                    try {
	                        String url = imagekitService.uploadFileToProduct(image);
	                        ImageInfo info = new ImageInfo();
	                        info.setUrl(url);
	                        return info;
	                    } catch (Exception e) {
	                        throw new RuntimeException("Image upload failed", e);
	                    }
	                }).collect(Collectors.toList());
	        product.setEncryptedImages(imageInfos);
	    }

	    // Save base product changes
	    product = productService.saveProduct(product);

	    // --- Update specifications if provided ---
	    if (specificationsJson != null) {
	        ObjectMapper mapper = new ObjectMapper();
	        List<SpecificationDTO> specDtos = mapper.readValue(specificationsJson, new TypeReference<>() {});

	        int specImageIndex = 0;

	        for (SpecificationDTO specDto : specDtos) {
	            Specification specification;
	            if (specDto.getId() != null) {
	                // Update existing spec
	                specification = specificationService.getSpecificationById(specDto.getId())
	                        .orElseThrow(() -> new RuntimeException("Specification not found: ID " + specDto.getId()));
	                specification.setName(specDto.getName());
	            } else {
	                // Create new spec
	                specification = new Specification();
	                specification.setName(specDto.getName());
	                specification.setProduct(product);
	            }
	            specification = specificationService.saveSpecification(specification);

	            for (SpecificationOptionDTO optDto : specDto.getOptions()) {
	                SpecificationOption option;
	                if (optDto.getId() != null) {
	                    option = specificationOptionService.getSpecificationOptionById(optDto.getId())
	                            .orElseThrow(() -> new RuntimeException("Option not found: ID " + optDto.getId()));
	                    option.setName(optDto.getName());
	                } else {
	                    option = new SpecificationOption();
	                    option.setName(optDto.getName());
	                    option.setSpecification(specification);
	                }

	                // Update option price
	                BigDecimal optPrice = new BigDecimal(optDto.getPrice().toString()).setScale(2, RoundingMode.HALF_UP);
	                option.setPrice(optPrice);

	                // Upload image if specImages are passed
	                if (specImages != null && specImageIndex < specImages.size()) {
	                    MultipartFile specImage = specImages.get(specImageIndex);
	                    if (specImage != null && !specImage.isEmpty()) {
	                        String imageUrl = imagekitService.uploadSpecificationImageFile(specImage);
	                        ImageInfo img = new ImageInfo();
	                        img.setUrl(imageUrl);
	                        option.setImage(img);
	                        specImageIndex++;
	                    }
	                }

	                specificationOptionService.saveSpecificationOption(option);
	            }
	        }
	    }

	    // --- Prepare response DTO ---
	    ProductDto dto = convertToProductDto(product);
	    List<Specification> specs = specificationService.getSpecificationsByProduct(product);
	    List<SpecificationDTO> fullSpecDtos = specs.stream().map(spec -> {
	        SpecificationDTO sDto = new SpecificationDTO();
	        sDto.setId(spec.getId());
	        sDto.setName(spec.getName());
	        List<SpecificationOptionDTO> optionDtos = specificationOptionService
	                .getSpecificationOptionsBySpecification(spec)
	                .stream()
	                .map(opt -> new SpecificationOptionDTO(opt.getId(), opt.getName(), opt.getImage(), opt.getPrice()))
	                .collect(Collectors.toList());
	        sDto.setOptions(optionDtos);
	        return sDto;
	    }).collect(Collectors.toList());

	    dto.setSpecifications(fullSpecDtos);
	    return ResponseEntity.ok(dto);
	}


	public MultipartFile compressImage(MultipartFile originalImage) throws IOException {
	    BufferedImage bufferedImage = ImageIO.read(originalImage.getInputStream());

	    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    Thumbnails.of(bufferedImage)
	            .size(800, 800)
	            .outputFormat("jpg")
	            .outputQuality(0.7)
	            .toOutputStream(outputStream);

	    byte[] compressedBytes = outputStream.toByteArray();

	    return new CustomMultipartFile(
	        originalImage.getName(),
	        originalImage.getOriginalFilename(),
	        originalImage.getContentType(),
	        compressedBytes
	    );
	}




}
